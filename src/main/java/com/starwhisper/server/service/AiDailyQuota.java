package com.starwhisper.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 解读的按日限额（塔罗解牌与易经解卦共用同一额度池）：
 * 每个 IP per-ip-daily-limit 次/天，全站 global-daily-limit 次/天，
 * 按 Asia/Shanghai 自然日重置；尝试即占额度（含供应商失败），防止刷接口。
 */
@Component
public class AiDailyQuota {

  private final int perIpLimit;
  private final int globalLimit;
  private final Map<String, Integer> attempts = new HashMap<>();
  private LocalDate quotaDay = today();
  private int total;

  public AiDailyQuota(@Value("${kimi.per-ip-daily-limit:3}") int perIpLimit,
                      @Value("${kimi.global-daily-limit:100}") int globalLimit) {
    this.perIpLimit = Math.max(0, perIpLimit);
    this.globalLimit = Math.max(0, globalLimit);
  }

  private static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }

  /**
   * 检查并占用一次额度；超限抛 IllegalStateException（消息原样透给前端）
   */
  public synchronized void consume(String ip) {
    if (!today().equals(quotaDay)) { quotaDay = today(); total = 0; attempts.clear(); }
    if (total >= globalLimit) throw new IllegalStateException("今日 AI 解读名额已用完，明天再来看看");
    if (attempts.getOrDefault(ip, 0) >= perIpLimit) throw new IllegalStateException("当前网络今日 AI 解读次数已用完，明天再来看看");
    total++;
    attempts.merge(ip, 1, Integer::sum);
  }
}
