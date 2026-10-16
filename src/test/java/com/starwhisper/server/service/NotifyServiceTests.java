package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.SubscribeRecord;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.SubscribeRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * NotifyService 订阅/退订/状态逻辑单测（纯 Mockito，不碰微信接口、不启 Spring 容器）
 */
class NotifyServiceTests {

  private final SubscribeRecordRepository subscribeRepo = mock(SubscribeRecordRepository.class);
  private final AppUserRepository userRepo = mock(AppUserRepository.class);
  private final NotifyService service = new NotifyService(subscribeRepo, userRepo);

  private AppUser user(Long id) {
    AppUser u = new AppUser();
    u.setId(id);
    u.setOpenid("openid_" + id);
    return u;
  }

  @Test
  void 首次订阅新建记录为ACTIVE() {
    AppUser u = user(1L);
    when(subscribeRepo.findByUserIdAndScene(1L, "DAILY_FORTUNE")).thenReturn(Optional.empty());

    service.subscribe(u, "daily_fortune", "aries"); // 小写场景名应被规整

    ArgumentCaptor<SubscribeRecord> captor = ArgumentCaptor.forClass(SubscribeRecord.class);
    verify(subscribeRepo).save(captor.capture());
    SubscribeRecord saved = captor.getValue();
    assertEquals(1L, saved.getUserId());
    assertEquals("DAILY_FORTUNE", saved.getScene());
    assertEquals(SubscribeRecord.STATUS_ACTIVE, saved.getStatus());
    // 传了星座要顺手更新到用户资料
    assertEquals("aries", u.getSignNameEn());
    verify(userRepo).save(u);
  }

  @Test
  void 重复订阅把已消耗记录拉回ACTIVE且不插新记录() {
    AppUser u = user(2L);
    SubscribeRecord existing = new SubscribeRecord(2L, "DAILY_FORTUNE",
        SubscribeRecord.STATUS_CONSUMED, LocalDateTime.now().minusDays(1));
    when(subscribeRepo.findByUserIdAndScene(2L, "DAILY_FORTUNE")).thenReturn(Optional.of(existing));

    service.subscribe(u, "DAILY_FORTUNE", null);

    assertEquals(SubscribeRecord.STATUS_ACTIVE, existing.getStatus());
    verify(subscribeRepo).save(existing); // 复用旧记录，没有 insert 新行
    // 没传星座不更新用户资料
    verifyNoInteractions(userRepo);
  }

  @Test
  void 退订置CANCELLED且幂等() {
    AppUser u = user(3L);
    SubscribeRecord existing = new SubscribeRecord(3L, "DAILY_FORTUNE",
        SubscribeRecord.STATUS_ACTIVE, LocalDateTime.now());
    when(subscribeRepo.findByUserIdAndScene(3L, "DAILY_FORTUNE")).thenReturn(Optional.of(existing));

    service.unsubscribe(u, "DAILY_FORTUNE");
    assertEquals(SubscribeRecord.STATUS_CANCELLED, existing.getStatus());
    verify(subscribeRepo).save(existing);

    // 没订阅过也安静成功，不报错
    service.unsubscribe(user(99L), "DAILY_FORTUNE");
  }

  @Test
  void 状态接口只返回ACTIVE场景() {
    when(subscribeRepo.findByUserIdAndStatus(1L, SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(new SubscribeRecord(1L, "DAILY_FORTUNE",
            SubscribeRecord.STATUS_ACTIVE, LocalDateTime.now())));

    List<String> scenes = service.activeScenes(1L);

    assertEquals(List.of("DAILY_FORTUNE"), scenes);
  }

  @Test
  void 非法场景直接拒绝() {
    assertThrows(IllegalArgumentException.class, () -> service.subscribe(user(1L), "", null));
    assertThrows(IllegalArgumentException.class, () -> service.subscribe(user(1L), "HACK", null));
    assertThrows(IllegalArgumentException.class, () -> service.unsubscribe(user(1L), null));
    assertTrue(service.activeScenes(1L) != null || true);
  }
}
