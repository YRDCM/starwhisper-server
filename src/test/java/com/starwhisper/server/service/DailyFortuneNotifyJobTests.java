package com.starwhisper.server.service;

import com.starwhisper.server.dto.FortuneVO;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.SubscribeRecord;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.SubscribeRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 每日运势推送任务单测：模板 ID 空跳过、成功消耗额度、失败不拖累他人（不真调微信）
 */
class DailyFortuneNotifyJobTests {

  private final SubscribeRecordRepository subscribeRepo = mock(SubscribeRecordRepository.class);
  private final AppUserRepository userRepo = mock(AppUserRepository.class);
  private final FortuneService fortuneService = mock(FortuneService.class);
  private final WechatAccessTokenManager tokenManager = mock(WechatAccessTokenManager.class);
  private final WechatSubscribeMessageClient messageClient = mock(WechatSubscribeMessageClient.class);

  private DailyFortuneNotifyJob job(String templateId) {
    return new DailyFortuneNotifyJob(subscribeRepo, userRepo, fortuneService,
        tokenManager, messageClient, templateId);
  }

  private SubscribeRecord activeRecord(long userId) {
    return new SubscribeRecord(userId, SubscribeRecord.SCENE_DAILY_FORTUNE,
        SubscribeRecord.STATUS_ACTIVE, LocalDateTime.now());
  }

  private AppUser user(long id, String sign) {
    AppUser u = new AppUser();
    u.setId(id);
    u.setOpenid("openid_" + id);
    u.setSignNameEn(sign);
    return u;
  }

  @Test
  void 模板ID为空整个任务跳过不报错() {
    job("").pushDailyFortune();
    job("   ").pushDailyFortune(); // 空白串同样视为未配置

    // 连库都不查，更不碰 token / 发送
    verifyNoInteractions(subscribeRepo, tokenManager, messageClient, fortuneService);
  }

  @Test
  void 发送成功后订阅记录置为已消耗() {
    SubscribeRecord record = activeRecord(1L);
    when(subscribeRepo.findBySceneAndStatus("DAILY_FORTUNE", SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(record));
    when(tokenManager.getAccessToken()).thenReturn("fake_token");
    when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L, "aries")));
    FortuneVO fortune = mock(FortuneVO.class);
    when(fortuneService.getFortune(eq("aries"), any(LocalDate.class))).thenReturn(fortune);
    when(messageClient.send(eq("fake_token"), eq("openid_1"), eq("tmpl_1"), any(), any()))
        .thenReturn(true);

    job("tmpl_1").pushDailyFortune();

    // 微信平台规则：一次授权一次推送，成功后额度消耗
    assertEquals(SubscribeRecord.STATUS_CONSUMED, record.getStatus());
    verify(subscribeRepo).save(record);
  }

  @Test
  void 单个用户失败不影响其他人且失败方保持ACTIVE() {
    SubscribeRecord ok = activeRecord(1L);
    SubscribeRecord bad = activeRecord(2L);
    when(subscribeRepo.findBySceneAndStatus("DAILY_FORTUNE", SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(bad, ok)); // 失败的排前面，验证不中断
    when(tokenManager.getAccessToken()).thenReturn("fake_token");
    when(userRepo.findById(2L)).thenReturn(Optional.of(user(2L, "leo")));
    when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L, "aries")));
    when(fortuneService.getFortune(eq("leo"), any())).thenThrow(new RuntimeException("运势取数炸了"));
    when(fortuneService.getFortune(eq("aries"), any())).thenReturn(mock(FortuneVO.class));
    when(messageClient.send(any(), eq("openid_1"), any(), any(), any())).thenReturn(true);

    job("tmpl_1").pushDailyFortune();

    assertEquals(SubscribeRecord.STATUS_ACTIVE, bad.getStatus());       // 失败不消耗，下轮重试
    assertEquals(SubscribeRecord.STATUS_CONSUMED, ok.getStatus());      // 后面的用户照常送达
  }

  @Test
  void 发送失败保持ACTIVE下轮重试() {
    SubscribeRecord record = activeRecord(1L);
    when(subscribeRepo.findBySceneAndStatus("DAILY_FORTUNE", SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(record));
    when(tokenManager.getAccessToken()).thenReturn("fake_token");
    when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L, "aries")));
    when(fortuneService.getFortune(eq("aries"), any())).thenReturn(mock(FortuneVO.class));
    when(messageClient.send(any(), any(), any(), any(), any())).thenReturn(false); // 微信返回错误码

    job("tmpl_1").pushDailyFortune();

    assertEquals(SubscribeRecord.STATUS_ACTIVE, record.getStatus());
    verify(subscribeRepo, never()).save(record);
  }

  @Test
  void 未设置星座的用户跳过且不消耗() {
    SubscribeRecord record = activeRecord(1L);
    when(subscribeRepo.findBySceneAndStatus("DAILY_FORTUNE", SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(record));
    when(tokenManager.getAccessToken()).thenReturn("fake_token");
    when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L, null)));

    job("tmpl_1").pushDailyFortune();

    assertEquals(SubscribeRecord.STATUS_ACTIVE, record.getStatus());
    verifyNoInteractions(messageClient);
  }

  @Test
  void access_token拿不到本轮整体放弃() {
    when(tokenManager.getAccessToken()).thenThrow(new IllegalStateException("微信订阅消息未配置"));
    when(subscribeRepo.findBySceneAndStatus("DAILY_FORTUNE", SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(activeRecord(1L)));

    job("tmpl_1").pushDailyFortune(); // 不抛异常

    verifyNoInteractions(messageClient, fortuneService);
  }

  @Test
  void 模板字段与71514模板一致且thing类型有20字符截断保护() {
    FortuneVO fortune = mock(FortuneVO.class);
    when(fortune.getOverallScore()).thenReturn(4);
    when(fortune.getLoveScore()).thenReturn(5);
    when(fortune.getLuckyColor()).thenReturn("金色");
    when(fortune.getFortuneDate()).thenReturn(LocalDate.of(2026, 4, 9));
    when(fortune.getSummary()).thenReturn("这是一段非常非常非常非常非常非常非常非常长的运势摘要");

    Map<String, Object> data = DailyFortuneNotifyJob.buildTemplateData(fortune);

    // 模板 71514：运势关键词 thing1 / 运势简述 thing2 / 日期 time3
    assertEquals(Map.of("value", "综合4星 爱情5星 金色"), data.get("thing1"));
    String thing2 = (String) ((Map<?, ?>) data.get("thing2")).get("value");
    assertTrue(thing2.length() <= 20, "thing 类型字段不能超过 20 字符");
    assertEquals(Map.of("value", "2026年4月9日"), data.get("time3"));
  }
}
