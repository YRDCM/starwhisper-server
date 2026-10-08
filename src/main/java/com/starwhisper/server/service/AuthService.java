package com.starwhisper.server.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.UserSession;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 登录认证业务层
 * 两条登录通道：
 * 1. 微信登录（jscode2session 换 openid）—— 正式通道
 * 2. dev 登录（免微信，发一个 dev_ 开头的伪 openid）—— 开发/测试通道，上线必须关
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

  /** 会话有效期：30 天 */
  private static final int SESSION_DAYS = 30;

  private final AppUserRepository appUserRepository;
  private final UserSessionRepository userSessionRepository;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String wechatAppid;
  private final String wechatSecret;
  private final boolean devLoginEnabled;

  public AuthService(AppUserRepository appUserRepository,
                     UserSessionRepository userSessionRepository,
                     @Value("${wechat.appid:}") String wechatAppid,
                     @Value("${wechat.secret:}") String wechatSecret,
                     @Value("${app.dev-login:true}") boolean devLoginEnabled) {
    this.appUserRepository = appUserRepository;
    this.userSessionRepository = userSessionRepository;
    this.wechatAppid = wechatAppid;
    this.wechatSecret = wechatSecret;
    this.devLoginEnabled = devLoginEnabled;

    // 微信接口超时：连接 5s / 读取 10s
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 微信登录：用小程序 wx.login() 拿到的 code 换 openid，再 upsert 用户并发会话
   */
  public Map<String, Object> wechatLogin(String code) {
    if (!StringUtils.hasText(code)) {
      throw new IllegalArgumentException("缺少参数 code（wx.login 拿到的临时凭证）");
    }
    if (!StringUtils.hasText(wechatAppid)) {
      throw new IllegalStateException("微信登录未配置");
    }

    // 微信返回的是 JSON 但 Content-Type 是 text/plain，先按 String 收再手动解析
    String raw = restClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https").host("api.weixin.qq.com").path("/sns/jscode2session")
            .queryParam("appid", wechatAppid)
            .queryParam("secret", wechatSecret)
            .queryParam("js_code", code)
            .queryParam("grant_type", "authorization_code")
            .build())
        .retrieve()
        .body(String.class);

    Map<String, Object> response;
    try {
      response = objectMapper.readValue(raw, new TypeReference<>() {
      });
    } catch (Exception e) {
      throw new IllegalStateException("微信接口返回无法解析：" + raw);
    }

    if (response == null) {
      throw new IllegalStateException("微信接口无响应");
    }
    // 微信的错误返回形如 {"errcode":40029,"errmsg":"invalid code"}
    Object errcode = response.get("errcode");
    if (errcode != null && !"0".equals(String.valueOf(errcode))) {
      throw new IllegalArgumentException("微信登录失败：" + response.get("errmsg"));
    }
    String openid = (String) response.get("openid");
    if (!StringUtils.hasText(openid)) {
      throw new IllegalStateException("微信接口没返回 openid");
    }

    AppUser user = upsertUser(openid, null);
    return buildLoginResult(user);
  }

  /**
   * dev 登录：开发期免微信，openid 用 "dev_" + 8 位随机串，昵称默认"星语旅人"
   */
  public Map<String, Object> devLogin(String nickname) {
    if (!devLoginEnabled) {
      throw new IllegalStateException("dev 登录已关闭（app.dev-login=false）");
    }
    String openid = "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    AppUser user = upsertUser(openid, nickname);
    log.info("dev 登录：openid={}, nickname={}", openid, user.getNickname());
    return buildLoginResult(user);
  }

  /**
   * 解析请求头里的 token：支持 "Bearer xxx" 或裸 token；
   * 找不到或已过期返回 null（过期会话顺手删掉，惰性清理）
   */
  public AppUser resolveToken(String tokenHeader) {
    if (!StringUtils.hasText(tokenHeader)) {
      return null;
    }
    String token = tokenHeader.trim();
    if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
      token = token.substring(7).trim();
    }
    if (token.isEmpty()) {
      return null;
    }
    return userSessionRepository.findById(token)
        .flatMap(session -> {
          if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionRepository.delete(session); // 过期会话惰性删除
            return java.util.Optional.empty();
          }
          return appUserRepository.findById(session.getUserId());
        })
        .orElse(null);
  }

  /**
   * 按 openid 找用户，没有就注册；更新最后登录时间
   */
  private AppUser upsertUser(String openid, String nickname) {
    AppUser user = appUserRepository.findByOpenid(openid).orElseGet(() -> {
      AppUser fresh = new AppUser();
      fresh.setOpenid(openid);
      fresh.setCreatedAt(LocalDateTime.now());
      if (StringUtils.hasText(nickname)) {
        fresh.setNickname(nickname.trim());
      }
      return fresh;
    });
    user.setLastLoginAt(LocalDateTime.now());
    return appUserRepository.save(user);
  }

  /**
   * 发会话并组装登录返回：{token, user:{id, nickname, avatarUrl}}
   */
  private Map<String, Object> buildLoginResult(AppUser user) {
    String token = UUID.randomUUID().toString().replace("-", "");
    LocalDateTime now = LocalDateTime.now();
    userSessionRepository.save(new UserSession(token, user.getId(), now, now.plusDays(SESSION_DAYS)));

    return Map.of(
        "token", token,
        "user", Map.of(
            "id", user.getId(),
            "nickname", user.getNickname(),
            "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl()
        )
    );
  }
}
