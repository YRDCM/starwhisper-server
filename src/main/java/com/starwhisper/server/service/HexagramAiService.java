package com.starwhisper.server.service;

import com.starwhisper.server.dto.HexagramVO;
import com.starwhisper.server.entity.BaguaHexagram;
import com.starwhisper.server.repository.BaguaHexagramRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 易经 AI 解卦（与塔罗解牌同一套限额与响应结构）。
 * 起卦无会话概念，解卦按卦面身份（本卦卦序 + 之卦卦序 + 变爻）缓存：
 * 同一卦面同一问题直接命中缓存；同一卦面换了问题则拒绝（一次起卦只生成一次解读）。
 * 卦辞/爻义全部从卦库（数据库）取出组装 prompt，AI 只做解读层。
 */
@Service
public class HexagramAiService {

  private final KimiTarotClient client;
  private final AiDailyQuota quota;
  private final BaguaHexagramRepository repository;
  private final Map<String, Entry> cache = new LinkedHashMap<>();
  private final java.util.Set<String> runningKeys = new java.util.HashSet<>();
  private int active;
  private static final long TTL = Duration.ofHours(2).toMillis();
  private static final int CACHE_CAP = 500;

  private static class Entry {
    final String question;
    final KimiTarotClient.Reading reading;
    final long created = System.currentTimeMillis();
    Entry(String question, KimiTarotClient.Reading reading) {
      this.question = question;
      this.reading = reading;
    }
  }

  @Autowired
  public HexagramAiService(KimiTarotClient client, AiDailyQuota quota, BaguaHexagramRepository repository) {
    this.client = client;
    this.quota = quota;
    this.repository = repository;
  }

  // 测试用便捷构造：自建独立额度池
  public HexagramAiService(KimiTarotClient client, BaguaHexagramRepository repository, int perIpLimit, int globalLimit) {
    this(client, new AiDailyQuota(perIpLimit, globalLimit), repository);
  }

  public KimiTarotClient.Reading read(Integer primarySeq, Integer changedSeq, List<Integer> changingLines,
                                      String question, String ip) {
    // 参数校验在前：非法请求不占额度、不调供应商
    if (question == null || question.length() > 300)
      throw new IllegalArgumentException("请提供本次卦面，问题不超过300字");
    if (primarySeq == null)
      throw new IllegalArgumentException("缺少本卦，请重新起卦后解读");
    List<Integer> lines = validateChangingLines(changingLines);
    if (changedSeq == null && !lines.isEmpty())
      throw new IllegalArgumentException("有变爻但缺少之卦，请重新起卦后解读");
    if (changedSeq != null && lines.isEmpty())
      throw new IllegalArgumentException("有之卦但缺少变爻，请重新起卦后解读");
    if (!client.enabled()) throw new IllegalStateException("AI 解卦尚未启用，卦辞与基础解读仍可正常阅读");

    BaguaHexagram primary = repository.findBySeqNumber(primarySeq)
        .orElseThrow(() -> new IllegalArgumentException("不认识的卦序：" + primarySeq + "（应为 1-64）"));
    BaguaHexagram changed = null;
    if (changedSeq != null) {
      changed = repository.findBySeqNumber(changedSeq)
          .orElseThrow(() -> new IllegalArgumentException("不认识的卦序：" + changedSeq + "（应为 1-64）"));
    }

    String key = primarySeq + "|" + Objects.toString(changedSeq, "-") + "|" + lines;
    String stripped = question.strip();
    final HexagramVO primaryVO = HexagramVO.of(primary);
    final HexagramVO changedVO = changed == null ? null : HexagramVO.of(changed);
    synchronized (this) {
      cache.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().created > TTL);
      Entry entry = cache.get(key);
      if (entry != null) {
        if (!Objects.equals(entry.question, stripped))
          throw new IllegalArgumentException("本卦已生成解读，请恢复原问题后重试，或重新起卦");
        return entry.reading;
      }
      if (active >= 2) throw new IllegalStateException("AI 正在忙，请稍后再试");
      if (runningKeys.contains(key)) throw new IllegalStateException("这一卦正在解读，请稍候再查看");
      quota.consume(ip);
      active++;
      runningKeys.add(key);
    }
    try {
      var result = client.readHexagram(primaryVO, changedVO, lines, stripped);
      synchronized (this) {
        if (cache.size() >= CACHE_CAP) {
          var oldest = cache.keySet().iterator();
          if (oldest.hasNext()) { oldest.next(); oldest.remove(); }
        }
        cache.put(key, new Entry(stripped, result));
      }
      return result;
    } finally {
      synchronized (this) { active--; runningKeys.remove(key); }
    }
  }

  /**
   * 变爻校验：null 视为无变爻；每爻须在 1-6 且不重复，返回排序后的副本
   */
  private static List<Integer> validateChangingLines(List<Integer> changingLines) {
    if (changingLines == null) return List.of();
    if (changingLines.size() > 6) throw new IllegalArgumentException("变爻最多 6 个，请重新起卦后解读");
    List<Integer> sorted = new ArrayList<>(changingLines);
    sorted.sort(null);
    for (int i = 0; i < sorted.size(); i++) {
      Integer pos = sorted.get(i);
      if (pos == null || pos < 1 || pos > 6)
        throw new IllegalArgumentException("变爻位置须在 1-6 之间，请重新起卦后解读");
      if (i > 0 && sorted.get(i - 1).equals(pos))
        throw new IllegalArgumentException("变爻位置重复，请重新起卦后解读");
    }
    return List.copyOf(sorted);
  }
}
