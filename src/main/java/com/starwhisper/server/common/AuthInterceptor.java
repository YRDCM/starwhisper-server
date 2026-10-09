package com.starwhisper.server.common;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：解析 Authorization 头，把当前用户塞进 request attribute
 * 原则：这里绝不主动拦人 —— 大部分接口（运势/塔罗/起卦）游客也能用；
 * 只有 /api/user/** 需要登录，没登录就返回 code=401 的业务错误（HTTP 仍是 200，小程序端按 code 处理最省事）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  private final AuthService authService;

  public AuthInterceptor(AuthService authService) {
    this.authService = authService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    // 解析 token，登录了就挂到 request 上，没登录就是 null，不拦截
    AppUser user = authService.resolveToken(request.getHeader("Authorization"));
    request.setAttribute(CurrentUser.ATTRIBUTE, user);

    // 只有 /api/user/** 和 /api/checkin/** 强制要求登录，其余接口游客可用
    // 例外：/api/checkin/summary 走 openid 参数查询（v4 契约），不需要 token
    String uri = request.getRequestURI();
    boolean needLogin = uri.startsWith("/api/user/")
        || (uri.startsWith("/api/checkin") && !uri.equals("/api/checkin/summary"));
    if (user == null && needLogin) {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
      return false;
    }
    return true;
  }
}
