package com.starwhisper.server.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.starwhisper.server.common.TokenHasher;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.UserSession;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 登录认证业务层。
 *
 * 两条登录通道：
 * 1. 微信登录（jscode2session 换 openid）—— 正式通道
 * 2. dev 登录（免微信）—— 仅开发/自测。默认关闭，且必须同时配置
 *    app.dev-login-token 并在请求头带 X-Dev-Token 才能用（双闸门）。
 *
 * 安全约定：
 * · 数据库只存 token 的 SHA-256 摘要（见 TokenHasher），不存明文
 * · 微信接口的原始响应含 openid / session_key，**绝不回传给客户端，也绝不原文落日志**
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /** 会话有效期：30 天 */
  private static final int SESSION_DAYS = 30;

  /** 上游错误信息回传长度上限，避免把整段上游报文透给前端 */
  private static final int MAX_UPSTREAM_MSG = 64;

  private final AppUserRepository appUserRepository;
  private final UserSessionRepository userSessionRepository;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String wechatAppid;
  private final String wechatSecret;
  private final boolean devLoginEnabled;
  private final String devLoginToken;

  public AuthService(AppUserRepository appUserRepository,
                     UserSessionRepository userSessionRepository,
                     @Value("${wechat.appid:}") String wechatAppid,
                     @Value("${wechat.secret:}") String wechatSecret,
                     @Value("${app.dev-login:false}") boolean devLoginEnabled,
                     @Value("${app.dev-login-token:}") String devLoginToken) {
    this.appUserRepository = appUserRepository;
    this.userSessionRepository = userSessionRepository;
    this.wechatAppid = wechatAppid;
    this.wechatSecret = wechatSecret;
    this.devLoginEnabled = devLoginEnabled;
    this.devLoginToken = devLoginToken;

    if (devLoginEnabled && !StringUtils.hasText(devLoginToken)) {
      log.warn("app.dev-login=true 但没有配置 app.dev-login-token：dev 登录通道仍处于关闭状态");
    }
    if (devLoginEnabled) {
      log.warn("⚠️ dev 登录通道已开启（仅应在开发环境使用）");
    }

    // 微信接口超时：连接 5s / 读取 10s
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 微信登录：用小程序 wx.login() 拿到的 code 换 openid，再 upsert 用户并发会话
   */
  @Transactional
  public Map<String, Object> wechatLogin(String code) {
    if (!StringUtils.hasText(code)) {
      throw new IllegalArgumentException("缺少参数 code（wx.login 拿到的临时凭证）");
    }
    if (!StringUtils.hasText(wechatAppid) || !StringUtils.hasText(wechatSecret)) {
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
      // ⚠️ raw 里含 openid 与 session_key：只记长度，不记内容，更不能回传给客户端
      log.warn("微信 jscode2session 响应无法解析，响应长度={}", raw == null ? 0 : raw.length());
      throw new IllegalStateException("微信登录失败，请稍后重试");
    }

    if (response == null) {
      throw new IllegalStateException("微信登录失败，请稍后重试");
    }
    // 微信的错误返回形如 {"errcode":40029,"errmsg":"invalid code"}
    Object errcode = response.get("errcode");
    if (errcode != null && !"0".equals(String.valueOf(errcode))) {
      String errmsg = truncate(String.valueOf(response.get("errmsg")));
      log.warn("微信 jscode2session 返回错误：errcode={}, errmsg={}", errcode, errmsg);
      throw new IllegalArgumentException("微信登录失败（" + errmsg + "）");
    }
    String openid = (String) response.get("openid");
    if (!StringUtils.hasText(openid)) {
      log.warn("微信 jscode2session 未返回 openid");
      throw new IllegalStateException("微信登录失败，请稍后重试");
    }

    AppUser user = upsertUser(openid, null);
    return buildLoginResult(user);
  }

  /**
   * dev 登录：开发期免微信。
   * 双闸门：app.dev-login 必须为 true，且请求头 X-Dev-Token 必须等于 app.dev-login-token。
   * 只开一个开关不会放行，避免"忘了关"就直接裸奔。
   */
  @Transactional
  public Map<String, Object> devLogin(String nickname, String providedToken) {
    if (!devLoginEnabled) {
      throw new IllegalArgumentException("dev 登录未启用");
    }
    if (!StringUtils.hasText(devLoginToken)) {
      throw new IllegalArgumentException("dev 登录未配置校验令牌，通道已关闭");
    }
    if (!TokenHasher.constantTimeEquals(devLoginToken, providedToken)) {
      log.warn("dev 登录令牌不匹配，已拒绝");
      throw new IllegalArgumentException("dev 登录令牌无效");
    }
    String openid = "dev_" + TokenHasher.newToken().substring(0, 8);
    AppUser user = upsertUser(openid, nickname);
    log.info("dev 登录：openid={}, nickname={}", openid, user.getNickname());
    return buildLoginResult(user);
  }

  /**
   * 解析请求头里的 token：支持 "Bearer xxx" 或裸 token。
   * 找到且未过期返回用户；否则返回 null（过期会话顺手删掉，惰性清理）。
   * 注意：库里存的是摘要，所以这里要先哈希再查主键。
   */
  @Transactional
  public AppUser resolveToken(String tokenHeader) {
    String raw = extractRawToken(tokenHeader);
    if (raw == null) {
      return null;
    }
    String hashed = TokenHasher.hash(raw);
    return userSessionRepository.findById(hashed)
        .flatMap(session -> {
          if (session.getExpiresAt() == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            userSessionRepository.delete(session); // 过期会话惰性删除
            return Optional.<AppUser>empty();
          }
          return appUserRepository.findById(session.getUserId());
        })
        .orElse(null);
  }

  /** 退出登录：删掉当前 token 对应的会话。令牌无效时静默成功（幂等） */
  @Transactional
  public void logout(String tokenHeader) {
    String raw = extractRawToken(tokenHeader);
    if (raw == null) {
      return;
    }
    String hashed = TokenHasher.hash(raw);
    userSessionRepository.findById(hashed).ifPresent(userSessionRepository::delete);
  }

  /** 定时清理过期会话，返回删除条数 */
  @Transactional
  public int cleanupExpiredSessions() {
    return userSessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
  }

  /** 从 Authorization 头里取出裸 token；拿不到返回 null */
  private String extractRawToken(String tokenHeader) {
    if (!StringUtils.hasText(tokenHeader)) {
      return null;
    }
    String token = tokenHeader.trim();
    if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
      token = token.substring(7).trim();
    }
    return token.isEmpty() ? null : token;
  }

  /**
   * 按 openid 找用户，没有就注册；更新最后登录时间
   * 昵称唯一（DB 有唯一索引兜底）：默认/传入昵称撞名时自动加 ·xxxx 后缀
   */
  private AppUser upsertUser(String openid, String nickname) {
    AppUser user = appUserRepository.findByOpenid(openid).orElseGet(() -> {
      AppUser fresh = new AppUser();
      fresh.setOpenid(openid);
      fresh.setCreatedAt(LocalDateTime.now());
      fresh.setNickname(uniqueNickname(nickname));
      return fresh;
    });
    user.setLastLoginAt(LocalDateTime.now());
    return appUserRepository.save(user);
  }

  /**
   * 生成不撞名的昵称：传了就用传入的（撞名加后缀），没传用「星语旅人」打底。
   * 后缀取 4 位随机十六进制，撞了重试，最多 10 次。
   */
  String uniqueNickname(String nickname) {
    String base = StringUtils.hasText(nickname) ? nickname.trim() : "星语旅人";
    if (appUserRepository.findByNickname(base).isEmpty()) {
      return base;
    }
    for (int i = 0; i < 10; i++) {
      String candidate = base + "·" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
      if (appUserRepository.findByNickname(candidate).isEmpty()) {
        return candidate;
      }
    }
    throw new IllegalStateException("昵称分配失败，请重试");
  }

  /**
   * 发会话并组装登录返回：{token, user:{id, nickname, avatarUrl}}
   * 注意：返回给客户端的是明文 token，库里存的是它的 SHA-256 摘要。
   */
  private Map<String, Object> buildLoginResult(AppUser user) {
    String rawToken = TokenHasher.newToken();
    LocalDateTime now = LocalDateTime.now();
    userSessionRepository.save(new UserSession(
        TokenHasher.hash(rawToken), user.getId(), now, now.plusDays(SESSION_DAYS)));

    return Map.of(
        "token", rawToken,
        "user", Map.of(
            "id", user.getId(),
            "nickname", user.getNickname(),
            "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl()
        )
    );
  }

  private static String truncate(String text) {
    if (text == null) {
      return "";
    }
    String trimmed = text.trim();
    return trimmed.length() <= MAX_UPSTREAM_MSG ? trimmed : trimmed.substring(0, MAX_UPSTREAM_MSG) + "…";
  }
}
