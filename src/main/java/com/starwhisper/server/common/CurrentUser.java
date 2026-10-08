package com.starwhisper.server.common;

import com.starwhisper.server.entity.AppUser;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户工具：拦截器把用户塞进 request attribute，业务代码从这里取
 */
public class CurrentUser {

  public static final String ATTRIBUTE = "currentUser";

  /**
   * 取当前登录用户，未登录返回 null（不抛异常，由业务决定要不要求登录）
   */
  public static AppUser get(HttpServletRequest request) {
    Object value = request.getAttribute(ATTRIBUTE);
    return value instanceof AppUser user ? user : null;
  }
}
