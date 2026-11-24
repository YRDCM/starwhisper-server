package com.starwhisper.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部数据源闸门：日配额 + 熔断，防止把 ShowAPI 免费额度打爆、也防止上游挂掉时
 * 每个用户请求都去撞一次超时。
 *
 * 规则：
 * 1. **尝试即占额度**（含失败）。理由：失败调用往往也计入上游配额，
 *    而且"失败不计数"会让重试把额度瞬间打光。
 * 2. 连续失败达到阈值 → 进入冷却期（默认 30 分钟），冷却期内直接拒绝，
 *    让业务立刻走本地兜底，而不是等满 15 秒超时。
 * 3. 按 Asia/Shanghai 自然日重置。
 *
 * 单机内存实现：当前只有一台服务器，够用。
 * 如果以后要多实例，把这个类的状态换成 Redis 即可（接口不变）。
 */
@Component
public class ExternalCallGuard {

  private static final Logger log = LoggerFactory.getLogger(ExternalCallGuard.class);

  private final int dailyLimit;
  private final int failureThreshold;
  private final long cooldownMillis;

  private final Map<String, State> states = new ConcurrentHashMap<>();

  public ExternalCallGuard(@Value("${showapi.daily-call-limit:90}") int dailyLimit,
                           @Value("${showapi.failure-threshold:3}") int failureThreshold,
                           @Value("${showapi.cooldown-minutes:30}") int cooldownMinutes) {
    this.dailyLimit = Math.max(1, dailyLimit);
    this.failureThreshold = Math.max(1, failureThreshold);
    this.cooldownMillis = Math.max(1, cooldownMinutes) * 60_000L;
  }

  /**
   * 申请一次外部调用额度。
   *
   * @return true 可以调用；false 表示今日额度已用尽或处于熔断冷却期，调用方应直接走兜底
   */
  public synchronized boolean allow(String channel) {
    State s = state(channel);
    rolloverIfNeeded(s);

    long now = System.currentTimeMillis();
    if (s.cooldownUntil > now) {
      return false;
    }
    if (s.calls >= dailyLimit) {
      return false;
    }
    s.calls++;
    return true;
  }

  /** 调用成功：重置连续失败计数 */
  public synchronized void recordSuccess(String channel) {
    State s = state(channel);
    s.consecutiveFailures = 0;
  }

  /** 调用失败：累计失败次数，达到阈值就进入冷却期 */
  public synchronized void recordFailure(String channel) {
    State s = state(channel);
    s.consecutiveFailures++;
    if (s.consecutiveFailures >= failureThreshold) {
      s.cooldownUntil = System.currentTimeMillis() + cooldownMillis;
      log.warn("外部数据源连续失败 {} 次，进入熔断，{} 分钟内不再调用：channel={}",
          s.consecutiveFailures, cooldownMillis / 60_000, channel);
      // 熔断重置计数，冷却结束后重新观察
      s.consecutiveFailures = 0;
    }
  }

  /** 今日已用次数（后台展示/排查用） */
  public synchronized int usedToday(String channel) {
    State s = state(channel);
    rolloverIfNeeded(s);
    return s.calls;
  }

  /** 是否处于熔断冷却期 */
  public synchronized boolean isCoolingDown(String channel) {
    State s = state(channel);
    return s.cooldownUntil > System.currentTimeMillis();
  }

  private State state(String channel) {
    return states.computeIfAbsent(channel, k -> new State());
  }

  private void rolloverIfNeeded(State s) {
    LocalDate today = LocalDate.now();
    if (!today.equals(s.day)) {
      s.day = today;
      s.calls = 0;
      s.consecutiveFailures = 0;
      s.cooldownUntil = 0L;
    }
  }

  private static final class State {
    private LocalDate day;
    private int calls;
    private int consecutiveFailures;
    private long cooldownUntil;
  }
}
