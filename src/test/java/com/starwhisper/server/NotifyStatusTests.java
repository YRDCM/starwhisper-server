package com.starwhisper.server;

import com.starwhisper.server.entity.SubscribeRecord;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.SubscribeRecordRepository;
import com.starwhisper.server.service.NotifyService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订阅状态查询单测。
 *
 * 重点是 ACTIVE 与 CONSUMED 必须分开返回：
 * 微信「一次授权只推一条」，推送成功后记录变 CONSUMED。
 * 如果状态接口只返回 ACTIVE，前端就无法区分「从没订阅过」和「刚收到推送、额度用完了」，
 * 用户会看到「未订阅」——明明刚收到消息，却像是没订上。
 */
class NotifyStatusTests {

  private final SubscribeRecordRepository repo = mock(SubscribeRecordRepository.class);
  private final AppUserRepository userRepo = mock(AppUserRepository.class);
  private final NotifyService service = new NotifyService(repo, userRepo);

  private SubscribeRecord record(String scene, String status) {
    return new SubscribeRecord(7L, scene, status, LocalDateTime.now());
  }

  @Test
  void 待推送额度与已推送额度分别返回() {
    when(repo.findByUserIdAndStatus(7L, SubscribeRecord.STATUS_ACTIVE))
        .thenReturn(List.of(record(SubscribeRecord.SCENE_WEEKLY_FORTUNE, SubscribeRecord.STATUS_ACTIVE)));
    when(repo.findByUserIdAndStatus(7L, SubscribeRecord.STATUS_CONSUMED))
        .thenReturn(List.of(record(SubscribeRecord.SCENE_DAILY_FORTUNE, SubscribeRecord.STATUS_CONSUMED)));

    assertEquals(List.of(SubscribeRecord.SCENE_WEEKLY_FORTUNE), service.activeScenes(7L));
    assertEquals(List.of(SubscribeRecord.SCENE_DAILY_FORTUNE), service.consumedScenes(7L));

    // 两个列表必须各自只查一种状态：查错状态会让「刚推完」和「还有额度」混在一起
    verify(repo).findByUserIdAndStatus(7L, SubscribeRecord.STATUS_ACTIVE);
    verify(repo).findByUserIdAndStatus(7L, SubscribeRecord.STATUS_CONSUMED);
  }

  @Test
  void 没有任何订阅时两个列表都是空的() {
    when(repo.findByUserIdAndStatus(7L, SubscribeRecord.STATUS_ACTIVE)).thenReturn(List.of());
    when(repo.findByUserIdAndStatus(7L, SubscribeRecord.STATUS_CONSUMED)).thenReturn(List.of());

    assertTrue(service.activeScenes(7L).isEmpty());
    assertTrue(service.consumedScenes(7L).isEmpty());
  }
}
