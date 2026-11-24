package com.starwhisper.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 解读的按日限额（塔罗解牌与易经解卦共用同一额度池）。
 *
 * 分两档，这是**登录的核心价值之一**：
 * · 游客：按 IP 计，默认 3 次/天
 * · 登录用户：按 userId 计，默认 8 次/天（只加不减——游客的额度不会被削减，
 *   所以不存在"为了登录而阉割功能"的问题）
 *
 * 全站还有一道总额度闸门（默认 100 次/天），防止成本失控。
 * 按 Asia/Shanghai 自然日重置；尝试即占额度（含供应商失败），防止刷接口。
 *
 * 单机内存实现：当前只有一台服务器。多实例部署时这里会翻倍，
 * 需要换成 Redis 才能保证全局限额准确。
 */
@Component
public class AiDailyQuota {

  private final int perIpDefault;
  private final int perUserDefault;
  private final int globalDefault;
  private final AppConfigService configService; // 为 null 表示固定限额（测试/兼容路径）

  /** 游客：按 IP 计数 */
  private final Map<String, Integer> ipAttempts = new HashMap<>();
  /** 登录用户：按 userId 计数 */
  private final Map<Long, Integer> userAttempts = new HashMap<>();
  private LocalDate quotaDay = today();
  private int total;

  @Autowired
  public AiDailyQuota(@Value("${kimi.per-ip-daily-limit:3}") int perIpLimit,
                      @Value("${kimi.per-user-daily-limit:8}") int perUserLimit,
                      @Value("${kimi.global-daily-limit:100}") int globalLimit,
                      AppConfigService configService) {
    this.perIpDefault = Math.max(0, perIpLimit);
    this.perUserDefault = Math.max(0, perUserLimit);
    this.globalDefault = Math.max(0, globalLimit);
    this.configService = configService;
  }

  /**
   * 测试/兼容构造：固定限额，不接配置中心。
   * 登录额度取游客额度的两倍，保证"登录不亏"。
   */
  public AiDailyQuota(int perIpLimit, int globalLimit) {
    this(perIpLimit, Math.max(perIpLimit * 2, perIpLimit), globalLimit, null);
  }

  /**
   * 兼容构造：游客限额与全站限额可动态，登录额度取游客的两倍。
   * （保留这个签名是为了不破坏已有的动态限额测试）
   */
  public AiDailyQuota(int perIpLimit, int globalLimit, AppConfigService configService) {
    this(perIpLimit, Math.max(perIpLimit * 2, perIpLimit), globalLimit, configService);
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai"));
  }

  /** 生效中的游客限额：配置中心优先，缺省回落构造默认值 */
  public int perIpLimit() {
    return configService != null
        ? configService.getInt(AppConfigService.KEY_AI_PER_IP, perIpDefault)
        : perIpDefault;
  }

  /** 生效中的登录用户限额（当前只读配置，不接后台动态改） */
  public int perUserLimit() {
    return perUserDefault;
  }

  /** 生效中的全站限额 */
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

  /** 兼容旧签名（无登录身份 = 游客） */
  public void consume(String ip) {
    consume(ip, null);
  }

  /**
   * 检查并占用一次额度；超限抛 IllegalStateException（消息原样透给前端）。
   *
   * @param ip     客户端 IP（游客按它计数）
   * @param userId 登录用户 id；为 null 表示游客
   */
  public synchronized void consume(String ip, Long userId) {
    rolloverIfNeeded();

    if (total >= globalLimit()) {
      throw new IllegalStateException("今日 AI 解读名额已用完，明天再来看看");
    }

    if (userId != null) {
      int limit = perUserLimit();
      int used = userAttempts.getOrDefault(userId, 0);
      if (used >= limit) {
        throw new IllegalStateException("今日 AI 解读次数已用完（登录用户 " + limit + " 次/天），明天再来");
      }
      userAttempts.merge(userId, 1, Integer::sum);
    } else {
      int limit = perIpLimit();
      int used = ipAttempts.getOrDefault(ip, 0);
      if (used >= limit) {
        // 提示里主动给出"登录能解锁更多"，这是给游客的转化钩子
        throw new IllegalStateException(
            "当前网络今日 AI 解读次数已用完（" + limit + " 次/天）。登录后可获得更多次数");
      }
      ipAttempts.merge(ip, 1, Integer::sum);
    }

    total++;
  }

  private void rolloverIfNeeded() {
    if (!today().equals(quotaDay)) {
      quotaDay = today();
      total = 0;
      ipAttempts.clear();
      userAttempts.clear();
    }
  }
}
