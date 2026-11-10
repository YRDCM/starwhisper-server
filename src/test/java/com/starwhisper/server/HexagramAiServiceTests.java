package com.starwhisper.server;

import com.starwhisper.server.entity.BaguaHexagram;
import com.starwhisper.server.repository.BaguaHexagramRepository;
import com.starwhisper.server.service.HexagramAiService;
import com.starwhisper.server.service.KimiTarotClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HexagramAiServiceTests {
  private final KimiTarotClient client = mock(KimiTarotClient.class);
  private final BaguaHexagramRepository repo = mock(BaguaHexagramRepository.class);
  private final KimiTarotClient.Reading reading = new KimiTarotClient.Reading("总览", "建议");

  private HexagramAiService service(int perIp, int global) {
    when(client.enabled()).thenReturn(true);
    when(client.readHexagram(any(), any(), anyList(), anyString())).thenReturn(reading);
    when(repo.findBySeqNumber(1)).thenReturn(Optional.of(
        new BaguaHexagram(1, "乾为天", "䷀", "乾", "乾", "111111", "元亨利贞", "自强不息", "上上")));
    when(repo.findBySeqNumber(2)).thenReturn(Optional.of(
        new BaguaHexagram(2, "坤为地", "䷁", "坤", "坤", "000000", "元亨，利牝马之贞", "厚德载物", "上上")));
    return new HexagramAiService(client, repo, perIp, global);
  }

  @Test void sameCastSameQuestionHitsCacheAndProviderCalledOnce() {
    var service = service(3, 100);
    var first = service.read(1, 2, List.of(1, 4), "事业", "ip");
    assertEquals(reading, first);
    assertEquals(reading, service.read(1, 2, List.of(4, 1), "事业", "ip")); // 变爻顺序无关
    verify(client, times(1)).readHexagram(any(), any(), eq(List.of(1, 4)), eq("事业"));
    // 同一卦面换问题：拒绝且不消耗额度
    assertThrows(IllegalArgumentException.class, () -> service.read(1, 2, List.of(1, 4), "感情", "ip"));
    verify(client, times(1)).readHexagram(any(), any(), anyList(), anyString());
  }

  @Test void invalidRequestsNeverConsumeQuotaNorCallProvider() {
    var service = service(1, 1);
    assertThrows(IllegalArgumentException.class, () -> service.read(null, null, null, "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(99, null, null, "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(1, null, List.of(3), "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(1, 2, null, "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(1, null, List.of(0), "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(1, null, List.of(2, 2), "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(1, null, null, "字".repeat(301), "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(1, null, null, null, "ip"));
    verify(client, never()).readHexagram(any(), any(), anyList(), anyString());
    // 校验失败不占额度：合法请求仍能成功
    assertEquals(reading, service.read(1, null, null, "", "ip"));
  }

  @Test void ipAndGlobalQuotasAreEnforced() {
    var service = service(1, 2);
    service.read(1, null, null, "", "a");
    var exhausted = assertThrows(IllegalStateException.class, () -> service.read(2, null, null, "", "a"));
    assertTrue(exhausted.getMessage().contains("当前网络"));
    service.read(2, null, null, "", "b");
    var global = assertThrows(IllegalStateException.class, () -> service.read(1, 2, List.of(1), "", "c"));
    assertTrue(global.getMessage().contains("名额已用完"));
    verify(client, times(2)).readHexagram(any(), any(), anyList(), anyString());
  }

  @Test void failedRequestConsumesQuotaButReleasesInFlightState() {
    var service = service(2, 100);
    when(client.readHexagram(any(), any(), anyList(), anyString()))
        .thenThrow(new IllegalStateException("provider unavailable")).thenReturn(reading);
    assertThrows(IllegalStateException.class, () -> service.read(1, null, null, "", "ip"));
    assertEquals(reading, service.read(1, null, null, "", "ip")); // 失败后同卦同问题可重试
    assertThrows(IllegalStateException.class, () -> service.read(2, null, null, "", "ip")); // 第三次超限
  }

  @Test void duplicateConcurrentRequestDoesNotCallProviderTwice() throws Exception {
    var service = service(3, 100);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(client.readHexagram(any(), any(), anyList(), anyString())).thenAnswer(invocation -> {
      entered.countDown();
      if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
      return reading;
    });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> service.read(1, null, null, "", "ip"));
      try {
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        var dup = assertThrows(IllegalStateException.class, () -> service.read(1, null, null, "", "ip"));
        assertTrue(dup.getMessage().contains("正在解读"));
      } finally { release.countDown(); }
      assertEquals(reading, first.get(3, TimeUnit.SECONDS));
      verify(client, times(1)).readHexagram(any(), any(), anyList(), anyString());
    }
  }

  @Test void missingKeyRejectsBeforeLoadingHexagram() {
    var service = new HexagramAiService(client, repo, 3, 100); // enabled() 默认 false
    var error = assertThrows(IllegalStateException.class, () -> service.read(1, null, null, "", "ip"));
    assertTrue(error.getMessage().contains("尚未启用"));
    verify(repo, never()).findBySeqNumber(any());
  }
}
