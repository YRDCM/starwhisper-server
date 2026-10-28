package com.starwhisper.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 日常维护任务：清理过期会话与陈旧缓存。
 *
 * 在此之前，user_session 只在「访问到过期的那个 token」时才惰性删一行，
 * horoscope_cache 则完全没人清理 —— 两张表都会无限增长。
 */
@Component
public class SessionMaintenanceJob {

  private static final Logger log = LoggerFactory.getLogger(SessionMaintenanceJob.class);

  /** 缓存保留天数：key 里带日期，超过这个天数的行不会再被读到 */
  private static final int CACHE_RETAIN_DAYS = 14;

  private final AuthService authService;
  private final HoroscopeCacheService cacheService;
  private final boolean enabled;

  public SessionMaintenanceJob(AuthService authService,
                               HoroscopeCacheService cacheService,
                               @Value("${app.maintenance.enabled:true}") boolean enabled) {
    this.authService = authService;
    this.cacheService = cacheService;
    this.enabled = enabled;
  }

  @Scheduled(cron = "${app.session-cleanup-cron:0 30 3 * * *}", zone = "Asia/Shanghai")
  public void cleanup() {
    if (!enabled) {
      return;
    }
    try {
      int sessions = authService.cleanupExpiredSessions();
      int cached = cacheService.cleanupOlderThan(LocalDate.now().minusDays(CACHE_RETAIN_DAYS));
      log.info("维护任务完成：清理过期会话 {} 条，清理陈旧缓存 {} 条", sessions, cached);
    } catch (Exception e) {
      // 维护任务失败不能影响服务；打日志等下次重试
      log.error("维护任务失败", e);
    }
  }
}
