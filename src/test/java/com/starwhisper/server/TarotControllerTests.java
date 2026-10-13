package com.starwhisper.server;

import com.starwhisper.server.controller.TarotController;
import com.starwhisper.server.service.TarotService;
import com.starwhisper.server.service.UserHistoryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TarotControllerTests {
  @ParameterizedTest
  @ValueSource(strings = {"1,", ",", "1,,22", "abc"})
  void rejectsMalformedSelectionBeforeDrawing(String selection) {
    TarotService tarot = mock(TarotService.class);
    UserHistoryService history = mock(UserHistoryService.class);
    TarotController controller = new TarotController(tarot, history);
    assertThrows(IllegalArgumentException.class,
        () -> controller.draw("single", null, selection, null));
    verifyNoInteractions(tarot, history);
  }
}
