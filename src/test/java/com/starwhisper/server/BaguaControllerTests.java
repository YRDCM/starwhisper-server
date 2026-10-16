package com.starwhisper.server;

import com.starwhisper.server.controller.BaguaController;
import com.starwhisper.server.service.BaguaService;
import com.starwhisper.server.service.HexagramAiService;
import com.starwhisper.server.service.KimiTarotClient;
import com.starwhisper.server.service.UserHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BaguaControllerTests {

  @Test void aiReadingPassesCastIdentityAndRemoteAddrToService() {
    HexagramAiService ai = mock(HexagramAiService.class);
    BaguaController controller = new BaguaController(mock(BaguaService.class), mock(UserHistoryService.class), ai);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    var reading = new KimiTarotClient.Reading("总览", "建议");
    when(ai.read(24, 2, List.of(1), "事业", "203.0.113.9")).thenReturn(reading);

    var result = controller.aiReading(new BaguaController.AiRequest(24, 2, List.of(1), "事业"), request);

    assertEquals(reading, result.getData());
    verify(ai).read(24, 2, List.of(1), "事业", "203.0.113.9");
  }
}
