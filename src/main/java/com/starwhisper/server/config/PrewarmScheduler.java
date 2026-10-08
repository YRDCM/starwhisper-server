package com.starwhisper.server.config;

import com.starwhisper.server.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日运势预热定时器
 * 默认每天 07:05 触发：ShowAPI 每天 1/7/17 点更新数据，7 点后拉到的就是当天最新；
 * 12 星座 = 12 次接口配额（免费档 100 次/天，够用）。
 * cron 可用环境变量 APP_PREWARM_CRON 覆盖；显式指定上海时区，服务器在别的时区也不跑偏
 */
@Component
public class PrewarmScheduler {

  private static final Logger log = LoggerFactory.getLogger(PrewarmScheduler.class);

  private final FortuneService fortuneService;

  public PrewarmScheduler(FortuneService fortuneService) {
    this.fortuneService = fortuneService;
  }

  @Scheduled(cron = "${app.prewarm-cron:0 5 7 * * *}", zone = "Asia/Shanghai")
  public void prewarm() {
    log.info("开始每日运势预热……");
    fortuneService.prewarmToday();
  }
}
