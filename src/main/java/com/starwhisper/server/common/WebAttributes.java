package com.starwhisper.server.common;

/**
 * 请求级 attribute 的键名集中定义，避免各处硬编码字符串拼错。
 */
public final class WebAttributes {

  /** 当前登录用户（由 AuthInterceptor 写入） */
  public static final String CURRENT_USER = "currentUser";

  /**
   * 本次请求是否已经以「业务错误」结束。
   * 因为所有响应都包成 HTTP 200 + body.code，单看 status 判断不出成败，
   * 所以由 GlobalExceptionHandler 在这里打标，供埋点拦截器判断。
   */
  public static final String BUSINESS_ERROR = "sw.businessError";

  private WebAttributes() {
  }
}
