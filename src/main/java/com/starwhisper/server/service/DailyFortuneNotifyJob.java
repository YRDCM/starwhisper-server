package com.starwhisper.server.service;

import com.starwhisper.server.dto.FortuneVO;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.SubscribeRecord;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.SubscribeRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日运势订阅推送定时任务
 *
 * 平台规则（微信订阅消息）：一次授权只能推送一次。
 * 因此本任务对每个用户发送成功后，会把对应订阅记录置为 CONSUMED（额度已消耗）；
 * 前端展示上应提示「推送已送达，想明天继续收请再次授权」，引导用户重新走订阅流程。
 *
 * 模板 ID 走配置 wx.notify.dailyFortuneTemplateId：
 * 没配置（运营还没去公众平台申请模板）时整个任务直接跳过，只打一行日志，不报错。
 */
@Component
public class DailyFortuneNotifyJob {

  private static final Logger log = LoggerFactory.getLogger(DailyFortuneNotifyJob.class);

  /** 点击推送消息跳转的小程序页面 */
  private static final String TARGET_PAGE = "pages/fortune/fortune";

  private final SubscribeRecordRepository subscribeRecordRepository;
  private final AppUserRepository appUserRepository;
  private final FortuneService fortuneService;
  private final WechatAccessTokenManager accessTokenManager;
  private final WechatSubscribeMessageClient messageClient;
  private final String dailyFortuneTemplateId;

  public DailyFortuneNotifyJob(SubscribeRecordRepository subscribeRecordRepository,
                               AppUserRepository appUserRepository,
                               FortuneService fortuneService,
                               WechatAccessTokenManager accessTokenManager,
                               WechatSubscribeMessageClient messageClient,
                               @Value("${wx.notify.dailyFortuneTemplateId:}") String dailyFortuneTemplateId) {
    this.subscribeRecordRepository = subscribeRecordRepository;
    this.appUserRepository = appUserRepository;
    this.fortuneService = fortuneService;
    this.accessTokenManager = accessTokenManager;
    this.messageClient = messageClient;
    this.dailyFortuneTemplateId = dailyFortuneTemplateId;
  }

  /**
   * 每天 08:00（Asia/Shanghai）推送当日运势给所有 ACTIVE 订阅用户。
   * cron 可用环境变量 APP_NOTIFY_CRON 覆盖。
   */
  @Scheduled(cron = "${app.notify-cron:0 0 8 * * *}", zone = "Asia/Shanghai")
  public void pushDailyFortune() {
    // 模板 ID 是运营在公众平台申请后回填的；没配就不做任何事，也不报错
    if (!StringUtils.hasText(dailyFortuneTemplateId)) {
      log.info("每日运势推送跳过：wx.notify.dailyFortuneTemplateId 未配置（模板 ID 请到公众平台申请后回填）");
      return;
    }

    List<SubscribeRecord> records = subscribeRecordRepository
        .findBySceneAndStatus(SubscribeRecord.SCENE_DAILY_FORTUNE, SubscribeRecord.STATUS_ACTIVE);
    if (records.isEmpty()) {
      log.info("每日运势推送：暂无待推送订阅");
      return;
    }

    String accessToken;
    try {
      accessToken = accessTokenManager.getAccessToken();
    } catch (Exception e) {
      // token 拿不到（未配置 appid/secret、微信接口异常）→ 本轮整体放弃，不消耗任何订阅额度
      log.warn("每日运势推送中止：获取 access_token 失败，{}", e.getMessage());
      return;
    }

    int success = 0;
    int failed = 0;
    LocalDate today = LocalDate.now();
    for (SubscribeRecord record : records) {
      try {
        if (pushOne(record, accessToken, today)) {
          success++;
        } else {
          failed++;
        }
      } catch (Exception e) {
        // 单个用户失败（运势取数异常等）不影响其他人
        failed++;
        log.warn("每日运势推送失败：userId={}, 原因={}", record.getUserId(), e.getMessage());
      }
    }
    log.info("每日运势推送完成：成功 {}，失败 {}，共 {}", success, failed, records.size());
  }

  /**
   * 推送单个用户。
   * 成功 → 订阅记录置 CONSUMED（一次授权一次推送，额度已用完）；
   * 失败/缺数据 → 保持 ACTIVE，下轮再试（不会消耗用户的授权额度）。
   */
  private boolean pushOne(SubscribeRecord record, String accessToken, LocalDate today) {
    AppUser user = appUserRepository.findById(record.getUserId()).orElse(null);
    if (user == null || !StringUtils.hasText(user.getOpenid())) {
      log.warn("每日运势推送跳过：userId={} 用户不存在或无 openid", record.getUserId());
      return false;
    }
    if (!StringUtils.hasText(user.getSignNameEn())) {
      // 用户还没选过星座，不知道推什么内容；保持 ACTIVE 等前端补登
      log.warn("每日运势推送跳过：userId={} 未设置星座", record.getUserId());
      return false;
    }

    FortuneVO fortune = fortuneService.getFortune(user.getSignNameEn(), today);
    boolean sent = messageClient.send(accessToken, user.getOpenid(), dailyFortuneTemplateId,
        TARGET_PAGE, buildTemplateData(fortune));
    if (sent) {
      // 微信平台规则：一次授权只能发一次，发送成功即消耗本次订阅额度
      record.setStatus(SubscribeRecord.STATUS_CONSUMED);
      record.setUpdatedAt(LocalDateTime.now());
      subscribeRecordRepository.save(record);
    }
    return sent;
  }

  /**
   * 组装模板字段。
   * ⚠️ key 必须与公众平台申请的模板字段一致（thing/phrase/number 等类型还有长度限制：
   * thing 不超过 20 字符，number 只能数字）。运营申请到模板后，按模板实际字段名调整这里。
   */
  static Map<String, Object> buildTemplateData(FortuneVO fortune) {
    Map<String, Object> data = new LinkedHashMap<>();
    String signLabel = (fortune.getSignEmoji() == null ? "" : fortune.getSignEmoji() + " ")
        + fortune.getSignName();
    data.put("thing1", Map.of("value", truncate(signLabel + " 今日运势", 20)));
    data.put("phrase2", Map.of("value", fortune.getFortuneDate() == null ? "" : fortune.getFortuneDate().toString()));
    String summary = fortune.getSummary() == null ? "查看今日运势详情" : fortune.getSummary();
    data.put("thing3", Map.of("value", truncate(summary, 20)));
    return data;
  }

  private static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 1) + "…";
  }
}
