package com.starwhisper.server;

import com.starwhisper.server.dto.DrawResultVO;
import com.starwhisper.server.service.KimiTarotClient;
import com.starwhisper.server.service.TarotAiService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TarotAiServiceTests {
  private final KimiTarotClient client = mock(KimiTarotClient.class);
  private final DrawResultVO draw = new DrawResultVO("single", "单牌指引", List.of());
  private final KimiTarotClient.Reading reading = new KimiTarotClient.Reading("总览", "建议");
  private TarotAiService service(int perIp, int global) {
    when(client.enabled()).thenReturn(true);
    when(client.read(any(), anyString())).thenReturn(reading);
    return new TarotAiService(client, perIp, global);
  }

  @Test void guestsCanReadActualDrawAndRetriesAreCached() {
    var service = service(1, 1);
    String id = service.register(draw);
    assertEquals(reading, service.read(id, "问题", "guest"));
    assertEquals(reading, service.read(id, "问题", "guest"));
    verify(client, times(1)).read(same(draw), eq("问题"));
    assertThrows(IllegalArgumentException.class, () -> service.read(id, "改问题", "guest"));
  }

  @Test void invalidIdsAndOversizedQuestionsNeverCallProvider() {
    var service = service(3, 100);
    String id = service.register(draw);
    assertThrows(IllegalArgumentException.class, () -> service.read("fake", "", "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(id, "字".repeat(301), "ip"));
    assertThrows(IllegalArgumentException.class, () -> service.read(id, null, "ip"));
    verify(client, never()).read(any(), any());
  }

  @Test void ipAndGlobalQuotasCoverDifferentDraws() {
    var service = service(1, 2);
    service.read(service.register(draw), "", "a");
    assertThrows(IllegalStateException.class, () -> service.read(service.register(draw), "", "a"));
    service.read(service.register(draw), "", "b");
    assertThrows(IllegalStateException.class, () -> service.read(service.register(draw), "", "c"));
    verify(client, times(2)).read(any(), any());
  }

  @Test void failedRequestsConsumeQuotaButReleaseInFlightState() {
    var service = service(2, 100);
    when(client.read(any(), any())).thenThrow(new IllegalStateException("provider unavailable")).thenReturn(reading);
    String id = service.register(draw);
    assertThrows(IllegalStateException.class, () -> service.read(id, "", "ip"));
    assertEquals(reading, service.read(id, "", "ip"));
    assertThrows(IllegalStateException.class, () -> service.read(service.register(draw), "", "ip"));
  }

  @Test void duplicateConcurrentRequestDoesNotCallProviderTwice() throws Exception {
    var service = service(3, 100);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(client.read(any(), any())).thenAnswer(invocation -> {
      entered.countDown();
      if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
      return reading;
    });
    String id = service.register(draw);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> service.read(id, "", "ip"));
      try {
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> service.read(id, "", "ip"));
      } finally { release.countDown(); }
      assertEquals(reading, first.get(3, TimeUnit.SECONDS));
      verify(client, times(1)).read(any(), any());
    }
  }

  @Test void missingKeyKeepsDrawAvailableWithoutAiId() {
    var service = new TarotAiService(client, 3, 100);
    assertNull(service.register(draw));
    assertThrows(IllegalStateException.class, () -> service.read("id", "", "ip"));
  }
}
