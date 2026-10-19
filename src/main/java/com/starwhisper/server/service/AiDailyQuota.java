package com.starwhisper.server.service;

import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * 限额来源（v5 动态化）：每次校验时经 AppConfigService 读 app_config 表里的
 * ai.perIpDailyLimit / ai.globalDailyLimit，DB 没记录时用构造传入的默认值
 * （即环境变量 kimi.per-ip-daily-limit / kimi.global-daily-limit）。
 * AppConfigService 内部有秒级小缓存，不会每次请求都查库；后台改完配置立即生效。
 * 测试可用两参构造传入 null 配置源，行为与旧版固定限额完全一致。
 */
@Component
public class AiDailyQuota {

  private final int perIpDefault;
  private final int globalDefault;
  private final AppConfigService configService; // 为 null 表示固定限额（测试/兼容路径）

  private final Map<String, Integer> attempts = new HashMap<>();
  private LocalDate quotaDay = today();
  private int total;

  @Autowired
  public AiDailyQuota(@Value("${kimi.per-ip-daily-limit:3}") int perIpLimit,
                      @Value("${kimi.global-daily-limit:100}") int globalLimit,
                      AppConfigService configService) {
    this.perIpDefault = Math.max(0, perIpLimit);
    this.globalDefault = Math.max(0, globalLimit);
    this.configService = configService;
  }

  /** 测试/兼容构造：固定限额，不接配置中心，行为与动态化之前一致 */
  public AiDailyQuota(int perIpLimit, int globalLimit) {
    this(perIpLimit, globalLimit, null);
  }

  private static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }

  /** 生效中的每 IP 限额：配置中心优先，缺省回落构造默认值 */
  public int perIpLimit() {
    return configService != null
        ? configService.getInt(AppConfigService.KEY_AI_PER_IP, perIpDefault)
        : perIpDefault;
  }

  /** 生效中的全站限额：配置中心优先，缺省回落构造默认值 */
  public int globalLimit() {
    return configService != null
        ? configService.getInt(AppConfigService.KEY_AI_GLOBAL, globalDefault)
        : globalDefault;
  }

  /** 今日已用次数（后台统计用；顺带完成跨天清零） */
  public synchronized int todayUsed() {
    rolloverIfNeeded();
    return total;
  }

  /** 全站今日剩余次数（后台统计用） */
  public synchronized int globalRemaining() {
    rolloverIfNeeded();
    return Math.max(0, globalLimit() - total);
  }

  /**
   * 检查并占用一次额度；超限抛 IllegalStateException（消息原样透给前端）
   * 限额每次校验实时读取，后台改配置立即生效，不用重启
   */
  public synchronized void consume(String ip) {
    rolloverIfNeeded();
    if (total >= globalLimit()) throw new IllegalStateException("今日 AI 解读名额已用完，明天再来看看");
    if (attempts.getOrDefault(ip, 0) >= perIpLimit()) throw new IllegalStateException("当前网络今日 AI 解读次数已用完，明天再来看看");
    total++;
    attempts.merge(ip, 1, Integer::sum);
  }

  private void rolloverIfNeeded() {
    if (!today().equals(quotaDay)) { quotaDay = today(); total = 0; attempts.clear(); }
  }
}
