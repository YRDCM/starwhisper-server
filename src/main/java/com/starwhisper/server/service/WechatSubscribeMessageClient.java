package com.starwhisper.server.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
  // miniprogram_state：formal=正式版 / trial=体验版 / developer=开发版。
  // 小程序未发布时 formal 会被微信 412 拒绝，联调期用 trial，发布后改回 formal
  private final String miniprogramState;

  public WechatSubscribeMessageClient(@Value("${wx.notify.miniprogram-state:formal}") String miniprogramState) {
    this.miniprogramState = miniprogramState;
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
    payload.put("miniprogram_state", miniprogramState);
    payload.put("lang", "zh_CN");

    String raw;
    try {
      // 手动序列化成 JSON 字符串再发：手写 RestClient 不一定注册 Jackson3(tools.jackson) 转换器，
      // 直接传 Map 可能发出非 JSON 报文被微信网关 412 拒收（2026-10-16 实测踩坑）
      String json = objectMapper.writeValueAsString(payload);
      raw = restClient.post()
          .uri("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(json)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      log.warn("订阅消息发送请求异常：openid={}, 原因={}", openid, e.getMessage());
      return false;
    }

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
