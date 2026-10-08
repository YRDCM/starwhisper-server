package com.starwhisper.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：所有 Controller 抛出的异常统一在这里兜底，
 * 保证前端永远收到 { code, message, data } 格式的响应，而不是一堆堆栈信息
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * 业务参数/状态异常：把具体错误信息透给前端（如"不认识的星座""微信登录未配置"），
   * 这类消息本来就是写给用户看的，不算泄露内部信息
   */
  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public Result<Void> handleBusinessException(RuntimeException e) {
    log.warn("业务异常：{}", e.getMessage());
    return Result.error(e.getMessage());
  }

  /**
   * 兜底处理所有未捕获异常
   */
  @ExceptionHandler(Exception.class)
  public Result<Void> handleException(Exception e) {
    // 服务端打日志留痕，方便排查
    log.error("接口异常：{}", e.getMessage(), e);
    // 返回给前端的提示不要太具体，避免泄露内部信息
    return Result.error("服务器开小差了，请稍后再试");
  }
}
