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
