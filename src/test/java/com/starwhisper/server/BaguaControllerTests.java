package com.starwhisper.server;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.controller.BaguaController;
import com.starwhisper.server.entity.AppUser;
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

  private static HttpServletRequest guestRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    when(request.getAttribute(CurrentUser.ATTRIBUTE)).thenReturn(null);
    return request;
  }

  @Test void aiReadingPassesCastIdentityAndRemoteAddrToService() {
    HexagramAiService ai = mock(HexagramAiService.class);
    BaguaController controller = new BaguaController(mock(BaguaService.class), mock(UserHistoryService.class), ai);
    HttpServletRequest request = guestRequest();
    var reading = new KimiTarotClient.Reading("总览", "建议");
    // 游客：userId 为 null，走游客额度
    when(ai.read(24, 2, List.of(1), "事业", "203.0.113.9", null)).thenReturn(reading);

    var result = controller.aiReading(new BaguaController.AiRequest(24, 2, List.of(1), "事业"), request);

    assertEquals(reading, result.getData());
    verify(ai).read(24, 2, List.of(1), "事业", "203.0.113.9", null);
  }

  @Test void aiReadingPassesLoggedInUserIdSoQuotaIsPerAccount() {
    HexagramAiService ai = mock(HexagramAiService.class);
    BaguaController controller = new BaguaController(mock(BaguaService.class), mock(UserHistoryService.class), ai);
    HttpServletRequest request = guestRequest();
    AppUser user = new AppUser();
    user.setId(42L);
    when(request.getAttribute(CurrentUser.ATTRIBUTE)).thenReturn(user);
    var reading = new KimiTarotClient.Reading("总览", "建议");
    // 登录用户：带 userId，走登录额度（比游客多）
    when(ai.read(24, 2, List.of(1), "事业", "203.0.113.9", 42L)).thenReturn(reading);

    var result = controller.aiReading(new BaguaController.AiRequest(24, 2, List.of(1), "事业"), request);

    assertEquals(reading, result.getData());
    verify(ai).read(24, 2, List.of(1), "事业", "203.0.113.9", 42L);
  }
}
