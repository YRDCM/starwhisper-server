package com.starwhisper.server.service;

import com.starwhisper.server.dto.DrawResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KimiTarotClientTests {
  @Test void sendsBoundedNonThinkingRequestAndParsesJson() {
    var builder = RestClient.builder().baseUrl("https://api.moonshot.cn/v1");
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new KimiTarotClient("test-only", "kimi-k2.6", builder.build());
    server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
        .andExpect(header("Authorization", "Bearer test-only"))
        .andExpect(jsonPath("$.thinking.type").value("disabled"))
        .andExpect(jsonPath("$.max_tokens").value(1800))
        .andRespond(withSuccess(new tools.jackson.databind.ObjectMapper().writeValueAsString(
            java.util.Map.of("choices", List.of(java.util.Map.of("finish_reason", "stop", "message",
                java.util.Map.of("content", "{\"summary\":\"总览\",\"detail\":\"建议\"}"))))), MediaType.APPLICATION_JSON));
    var result = client.read(new DrawResultVO("single", "单牌指引", List.of()), "");
    assertEquals("总览", result.summary());
    server.verify();
  }

  @Test void providerErrorNeverExposesRawResponse() {
    var builder = RestClient.builder().baseUrl("https://api.moonshot.cn/v1");
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new KimiTarotClient("test-only", "kimi-k2.6", builder.build());
    server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
        .andRespond(withBadRequest().body("sensitive-provider-debug"));
    var error = assertThrows(IllegalStateException.class,
        () -> client.read(new DrawResultVO("single", "单牌指引", List.of()), ""));
    assertFalse(error.getMessage().contains("sensitive"));
    assertNull(error.getCause());
    server.verify();
  }
}
