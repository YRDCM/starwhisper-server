package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * 微信登录：POST /api/auth/wechat，body {"code": "wx.login 拿到的临时凭证"}
   * 返回 {token, user:{id, nickname, avatarUrl}}
   */
  @PostMapping("/wechat")
  public Result<Map<String, Object>> wechat(@RequestBody(required = false) Map<String, String> body) {
    String code = (body == null) ? null : body.get("code");
    return Result.success(authService.wechatLogin(code));
  }

  /**
   * dev 登录（开发期专用）：POST /api/auth/dev，body {"nickname": "可选"}
   *
   * 双闸门：服务端 app.dev-login=true **且** 请求头 X-Dev-Token 等于 app.dev-login-token。
   * 只改一个开关不会放行。
   */
  @PostMapping("/dev")
  public Result<Map<String, Object>> dev(@RequestBody(required = false) Map<String, String> body,
                                         @RequestHeader(value = "X-Dev-Token", required = false) String devToken) {
    String nickname = (body == null) ? null : body.get("nickname");
    return Result.success(authService.devLogin(nickname, devToken));
  }

  /**
   * 退出登录：POST /api/auth/logout（幂等，令牌无效也返回成功）
   * 从前端"我的"页调用，服务端删掉对应会话行
   */
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request) {
    authService.logout(request.getHeader("Authorization"));
    return Result.ok0(null);
  }
}
