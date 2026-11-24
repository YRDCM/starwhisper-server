package com.starwhisper.server.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 时区统一为 Asia/Shanghai。
 *
 * 为什么必须显式设置：定时任务写的是 {@code @Scheduled(zone = "Asia/Shanghai")}，
 * 但业务代码里到处是 {@link java.time.LocalDate#now()}，取的是 **JVM 默认时区**。
 * 如果服务器是 UTC（云主机默认常见），07:05 北京时间触发预热时，
 * LocalDate.now() 算出来的是"前一天" → 预热写错日期、推送推错运势，而且完全不会报错。
 *
 * 这里在容器启动时把 JVM 默认时区钉死，让两者一致。
 */
@Configuration
public class TimeZoneConfig {

  private static final Logger log = LoggerFactory.getLogger(TimeZoneConfig.class);

  static final String APP_ZONE = "Asia/Shanghai";

  @PostConstruct
  void setDefaultTimeZone() {
    TimeZone before = TimeZone.getDefault();
    TimeZone target = TimeZone.getTimeZone(APP_ZONE);
    if (!before.equals(target)) {
      TimeZone.setDefault(target);
      log.warn("JVM 默认时区 {} 不是 {}，已强制切换（否则定时任务与业务日期会错开）",
          before.getID(), APP_ZONE);
    }
    log.info("业务时区：{}（当前时间 {}）", APP_ZONE, java.time.LocalDateTime.now());
  }
}
