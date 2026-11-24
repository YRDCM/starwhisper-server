package com.starwhisper.server.service;

import com.starwhisper.server.dto.HexagramVO;
import com.starwhisper.server.entity.BaguaHexagram;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KimiHexagramClientTests {

  private static HexagramVO gua(int seq, String name, String lines, String judgment, String meaning) {
    return HexagramVO.of(new BaguaHexagram(seq, name, "䷀", "乾", "坤", lines, judgment, meaning, "上上"));
  }

  @Test void sendsJudgmentFromDatabaseAndParsesJson() {
    var builder = RestClient.builder().baseUrl("https://api.moonshot.cn/v1");
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new KimiTarotClient("test-only", "kimi-k2.6", builder.build());
    server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
        .andExpect(header("Authorization", "Bearer test-only"))
        .andExpect(jsonPath("$.thinking.type").value("disabled"))
        .andExpect(jsonPath("$.response_format.type").value("json_object"))
        // 卦辞/卦名来自卦库，原样进 prompt；之卦与变爻也带上
        .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("元亨利贞"),
            org.hamcrest.Matchers.containsString("乾为天"),
            org.hamcrest.Matchers.containsString("坤为地"),
            org.hamcrest.Matchers.containsString("换工作"))))
        .andRespond(withSuccess(new tools.jackson.databind.ObjectMapper().writeValueAsString(
            Map.of("choices", List.of(Map.of("finish_reason", "stop", "message",
                Map.of("content", "{\"summary\":\"卦象总览\",\"detail\":\"卦象建议\"}"))))), MediaType.APPLICATION_JSON));
    var result = client.readHexagram(
        gua(1, "乾为天", "111111", "元亨利贞", "自强不息"),
        gua(2, "坤为地", "000000", "元亨，利牝马之贞", "厚德载物"),
        List.of(1, 4), "  换工作  ");
    assertEquals("卦象总览", result.summary());
    assertEquals("卦象建议", result.detail());
    server.verify();
  }

  @Test void noChangedHexagramOmitsChangedPayload() {
    var builder = RestClient.builder().baseUrl("https://api.moonshot.cn/v1");
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new KimiTarotClient("test-only", "kimi-k2.6", builder.build());
    server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
        .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("乾为天"),
            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("坤为地")))))
        .andRespond(withSuccess(new tools.jackson.databind.ObjectMapper().writeValueAsString(
            Map.of("choices", List.of(Map.of("finish_reason", "stop", "message",
                Map.of("content", "{\"summary\":\"总览\",\"detail\":\"建议\"}"))))), MediaType.APPLICATION_JSON));
    var result = client.readHexagram(gua(1, "乾为天", "111111", "元亨利贞", "自强不息"), null, List.of(), "");
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
        () -> client.readHexagram(gua(1, "乾为天", "111111", "元亨利贞", "自强不息"), null, List.of(), ""));
    assertFalse(error.getMessage().contains("sensitive"));
    assertTrue(error.getMessage().contains("原卦象已保留"));
    assertNull(error.getCause());
    server.verify();
  }
}
