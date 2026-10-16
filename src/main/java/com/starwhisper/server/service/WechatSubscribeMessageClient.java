package com.starwhisper.server.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信订阅消息发送客户端
 * 文档：https://developers.weixin.qq.com/miniprogram/dev/server/API/open-api/subscribe-message/api-wxaapi_requestsubscribemessage.html
 *
 * 平台规则（重要）：订阅消息是「一次授权只能发一次」——用户在小程序里每点一次「允许」，
 * 我们就获得一次推送额度，发完即消耗。想每天推，必须引导用户再次授权。
 */
@Component
public class WechatSubscribeMessageClient {

  private static final Logger log = LoggerFactory.getLogger(WechatSubscribeMessageClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public WechatSubscribeMessageClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 发送一条订阅消息
   *
   * @param accessToken 微信 access_token（由 WechatAccessTokenManager 提供）
   * @param openid      接收者 openid
   * @param templateId  订阅消息模板 ID（公众平台申请）
   * @param page        点击消息跳转的小程序页面路径，可空
   * @param data        模板字段，key 必须与模板定义一致（如 thing1/phrase2），值形如 {"value": "..."}
   * @return true=发送成功；false=微信返回了错误码（已记日志，调用方自行决定是否重试）
   */
  public boolean send(String accessToken, String openid, String templateId, String page, Map<String, Object> data) {
    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("touser", openid);
    payload.put("template_id", templateId);
    if (page != null && !page.isBlank()) {
      payload.put("page", page);
    }
    payload.put("data", data);
    // miniprogram_state：正式版；上线前联调可改 trial
    payload.put("miniprogram_state", "formal");
    payload.put("lang", "zh_CN");

    String raw = restClient.post()
        .uri("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken)
        .body(payload)
        .retrieve()
        .body(String.class);

    Map<String, Object> response;
    try {
      response = objectMapper.readValue(raw, new TypeReference<>() {
      });
    } catch (Exception e) {
      log.warn("订阅消息发送返回无法解析：openid={}", openid);
      return false;
    }
    // 成功：{"errcode":0,"errmsg":"ok"}；失败形如 {"errcode":43101,"errmsg":"user refuse to accept the msg"}
    Object errcode = response == null ? null : response.get("errcode");
    boolean ok = errcode != null && "0".equals(String.valueOf(errcode));
    if (!ok) {
      // errcode/errmsg 不含密钥，可安全记录；43101 通常是授权次数已用完
      log.warn("订阅消息发送失败：openid={}, errcode={}, errmsg={}", openid, errcode,
          response == null ? "无响应" : response.get("errmsg"));
    }
    return ok;
  }
}
