package com.starwhisper.server.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * 微信 access_token 管理（带缓存）
 *
 * 微信规则：access_token 有效期 7200 秒，重复获取会顶掉旧的（有 5 分钟兼容期），
 * 全平台共用同一额度，必须缓存，不能每次都现取。
 * 策略：缓存到「过期前 5 分钟」，临界点提前刷新，避免推送途中 token 失效。
 *
 * 注意：appid/secret 从 wechat.* 配置读（与微信登录共用），绝不写进代码或日志。
 */
@Component
public class WechatAccessTokenManager {

  private static final Logger log = LoggerFactory.getLogger(WechatAccessTokenManager.class);

  /** 提前刷新窗口：5 分钟 */
  private static final long REFRESH_AHEAD_SECONDS = 300;

  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String wechatAppid;
  private final String wechatSecret;

  // 缓存的 token 与其过期时刻（epoch 秒）；null 表示还没取过
  private String cachedToken;
  private Instant expiresAt;

  public WechatAccessTokenManager(@Value("${wechat.appid:}") String wechatAppid,
                                  @Value("${wechat.secret:}") String wechatSecret) {
    this.wechatAppid = wechatAppid;
    this.wechatSecret = wechatSecret;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 取可用的 access_token：缓存有效直接返回，否则重新获取。
   * synchronized：定时任务与接口并发时只会有一个请求去打微信。
   */
  public synchronized String getAccessToken() {
    if (!StringUtils.hasText(wechatAppid) || !StringUtils.hasText(wechatSecret)) {
      throw new IllegalStateException("微信订阅消息未配置（wechat.appid/wechat.secret 为空）");
    }
    if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
      return cachedToken;
    }

    // 微信返回 JSON 但 Content-Type 是 text/plain，按 String 收再手动解析（同 AuthService 的处理）
    String raw = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https").host("api.weixin.qq.com").path("/cgi-bin/token")
            .queryParam("grant_type", "client_credential")
            .queryParam("appid", wechatAppid)
            .queryParam("secret", wechatSecret)
            .build())
        .retrieve()
        .body(String.class);

    Map<String, Object> response;
    try {
      response = objectMapper.readValue(raw, new TypeReference<>() {
      });
    } catch (Exception e) {
      throw new IllegalStateException("微信 token 接口返回无法解析");
    }
    if (response == null || !StringUtils.hasText((String) response.get("access_token"))) {
      // 失败形如 {"errcode":40013,"errmsg":"invalid appid"}；errmsg 不含密钥，可安全记录
      Object errmsg = response == null ? "无响应" : response.get("errmsg");
      throw new IllegalStateException("获取微信 access_token 失败：" + errmsg);
    }

    // expires_in 正常为 7200；兜底按 7000 算，再提前 5 分钟刷新
    long expiresIn = 7000;
    Object rawExpires = response.get("expires_in");
    if (rawExpires instanceof Number n) {
      expiresIn = n.longValue();
    }
    this.cachedToken = (String) response.get("access_token");
    this.expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - REFRESH_AHEAD_SECONDS));
    log.info("微信 access_token 已刷新，{} 秒后过期", expiresIn);
    return cachedToken;
  }
}
