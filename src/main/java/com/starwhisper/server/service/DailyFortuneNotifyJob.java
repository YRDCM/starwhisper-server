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
   *
   * 注：@Scheduled 要求方法返回 void，所以这里只做委托；
   * 真正的实现在 runDailyPush()，后台可以手动触发它来做推送联调。
   */
  @Scheduled(cron = "${app.notify-cron:0 0 8 * * *}", zone = "Asia/Shanghai")
  public void pushDailyFortune() {
    runDailyPush();
  }

  /**
   * 执行一次推送，返回执行结果（供后台手动触发/排查用）。
   * 结果字段：skipped(是否跳过)、reason(跳过原因)、total、success、failed
   */
  public Map<String, Object> runDailyPush() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("skipped", false);

    // 模板 ID 是运营在公众平台申请后回填的；没配就不做任何事，也不报错
    if (!StringUtils.hasText(dailyFortuneTemplateId)) {
      log.info("每日运势推送跳过：wx.notify.dailyFortuneTemplateId 未配置（模板 ID 请到公众平台申请后回填）");
      result.put("skipped", true);
      result.put("reason", "模板 ID 未配置");
      result.put("total", 0);
      result.put("success", 0);
      result.put("failed", 0);
      return result;
    }

    List<SubscribeRecord> records = subscribeRecordRepository
        .findBySceneAndStatus(SubscribeRecord.SCENE_DAILY_FORTUNE, SubscribeRecord.STATUS_ACTIVE);
    if (records.isEmpty()) {
      log.info("每日运势推送：暂无待推送订阅");
      result.put("skipped", true);
      result.put("reason", "暂无待推送订阅（用户还没授权，或额度已用完）");
      result.put("total", 0);
      result.put("success", 0);
      result.put("failed", 0);
      return result;
    }

    String accessToken;
    try {
      accessToken = accessTokenManager.getAccessToken();
    } catch (Exception e) {
      // token 拿不到（未配置 appid/secret、微信接口异常）→ 本轮整体放弃，不消耗任何订阅额度
      log.warn("每日运势推送中止：获取 access_token 失败，{}", e.getMessage());
      result.put("skipped", true);
      result.put("reason", "获取 access_token 失败：" + e.getMessage());
      result.put("total", records.size());
      result.put("success", 0);
      result.put("failed", 0);
      return result;
    }

    int success = 0;
    int failed = 0;
    int expired = 0;
    LocalDate today = LocalDate.now();
    for (SubscribeRecord record : records) {
      try {
        int r = pushOne(record, accessToken, today);
        if (r == PUSH_OK) {
          success++;
        } else if (r == PUSH_EXPIRED) {
          expired++;
        } else {
          failed++;
        }
      } catch (Exception e) {
        // 单个用户失败（运势取数异常等）不影响其他人
        failed++;
        log.warn("每日运势推送失败：userId={}, 原因={}", record.getUserId(), e.getMessage());
      }
    }
    log.info("每日运势推送完成：成功 {}，失效 {}，失败 {}，共 {}", success, expired, failed, records.size());
    result.put("total", records.size());
    result.put("success", success);
    result.put("expired", expired);
    result.put("failed", failed);
    return result;
  }

  private static final int PUSH_OK = 1;
  private static final int PUSH_FAILED = 0;
  private static final int PUSH_EXPIRED = 2;

  /**
   * 推送单个用户。
   *
   * 微信侧的错误分两类，处理方式必须不同：
   * · 43101（用户没有有效授权）/ 40003（openid 无效）→ **重试永远不会成功**，
   *   把记录置为 CANCELLED，让前端能重新引导授权；否则这个任务每天早上都会
   *   对着同一条死记录失败一次，日志里全是噪音。
   * · 其它错误（网络抖动、供应商异常）→ 保持 ACTIVE，下次再试，不消耗用户额度。
   */
  private int pushOne(SubscribeRecord record, String accessToken, LocalDate today) {
    AppUser user = appUserRepository.findById(record.getUserId()).orElse(null);
    if (user == null || !StringUtils.hasText(user.getOpenid())) {
      log.warn("每日运势推送跳过：userId={} 用户不存在或无 openid", record.getUserId());
      return PUSH_FAILED;
    }
    if (!StringUtils.hasText(user.getSignNameEn())) {
      // 用户还没选过星座，不知道推什么内容；保持 ACTIVE 等前端补登
      log.warn("每日运势推送跳过：userId={} 未设置星座", record.getUserId());
      return PUSH_FAILED;
    }

    FortuneVO fortune = fortuneService.getFortune(user.getSignNameEn(), today);
    var sendResult = messageClient.sendWithResult(accessToken, user.getOpenid(), dailyFortuneTemplateId,
        TARGET_PAGE, buildTemplateData(fortune));

    if (sendResult.ok()) {
      // 微信平台规则：一次授权只能发一次，发送成功即消耗本次订阅额度
      record.setStatus(SubscribeRecord.STATUS_CONSUMED);
      record.setUpdatedAt(LocalDateTime.now());
      subscribeRecordRepository.save(record);
      return PUSH_OK;
    }

    if (sendResult.isUserRefused() || sendResult.isBadUser()) {
      // 授权已失效：不再重试，置为 CANCELLED，前端会显示「未订阅 · 点击开启」引导重新授权
      record.setStatus(SubscribeRecord.STATUS_CANCELLED);
      record.setUpdatedAt(LocalDateTime.now());
      subscribeRecordRepository.save(record);
      log.info("订阅已失效并标记取消：userId={}, errcode={}（用户需要重新授权）",
          record.getUserId(), sendResult.errcode());
      return PUSH_EXPIRED;
    }

    return PUSH_FAILED;
  }

  /**
   * 组装模板字段（模板 71514「今日运势提醒」，2026-10-16 公众平台详情页核对）：
   *   运势关键词 → thing1（≤20 字符，如"综合4星 爱情4星 金色"）
   *   运势简述   → thing2（≤20 字符，运势摘要截断）
   *   日期       → time3（time 类型，"2026年10月16日" 格式）
   */
  static Map<String, Object> buildTemplateData(FortuneVO fortune) {
    Map<String, Object> data = new LinkedHashMap<>();
    // 运势关键词：分数 + 幸运色拼成一行短词，超长自动截断
    StringBuilder keyword = new StringBuilder();
    if (fortune.getOverallScore() != null) keyword.append("综合").append(fortune.getOverallScore()).append("星");
    if (fortune.getLoveScore() != null) keyword.append(" 爱情").append(fortune.getLoveScore()).append("星");
    if (fortune.getLuckyColor() != null) keyword.append(" ").append(fortune.getLuckyColor());
    data.put("thing1", Map.of("value", truncate(keyword.length() == 0 ? "查看今日运势" : keyword.toString(), 20)));
    String summary = fortune.getSummary() == null ? "查看今日运势详情" : fortune.getSummary();
    data.put("thing2", Map.of("value", shortenSentence(summary, 20)));
    LocalDate date = fortune.getFortuneDate() == null ? LocalDate.now() : fortune.getFortuneDate();
    data.put("time3", Map.of("value", date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日"));
    return data;
  }

  private static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 1) + "…";
  }

  /**
   * 截断成一句读得通的话：thing 类型上限 20 字，直接硬切会停在半个词上，
   * 也常常留下一个悬空的逗号。这里切完之后去掉尾部标点，再补省略号。
   */
  static String shortenSentence(String text, int max) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    if (text.length() <= max) {
      return text;
    }
    String cut = text.substring(0, max - 1);
    // 末尾如果是标点，去掉它再补省略号，避免出现「……，…」这种别扭的收尾
    while (!cut.isEmpty() && "，、；：,;:".indexOf(cut.charAt(cut.length() - 1)) >= 0) {
      cut = cut.substring(0, cut.length() - 1);
    }
    return cut + "…";
  }
}
