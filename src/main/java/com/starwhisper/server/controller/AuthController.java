package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  // 构造注入
  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * 微信登录：POST /api/auth/wechat，body {"code": "wx.login 拿到的临时凭证"}
   * 返回 {token, user:{id, nickname, avatarUrl}}
   */
  @PostMapping("/wechat")
  public Result<Map<String, Object>> wechat(@RequestBody Map<String, String> body) {
    return Result.success(authService.wechatLogin(body.get("code")));
  }

  /**
   * dev 登录（开发期专用）：POST /api/auth/dev，body {"nickname": "可选"}
   * 返回结构同微信登录
   */
  @PostMapping("/dev")
  public Result<Map<String, Object>> dev(@RequestBody(required = false) Map<String, String> body) {
    String nickname = (body == null) ? null : body.get("nickname");
    return Result.success(authService.devLogin(nickname));
  }
}
