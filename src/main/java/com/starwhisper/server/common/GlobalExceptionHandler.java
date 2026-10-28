package com.starwhisper.server.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 全局异常处理器：所有 Controller 抛出的异常统一在这里兜底，
 * 保证前端永远收到 { code, message, data } 格式的响应，而不是堆栈信息。
 *
 * 两条原则：
 * 1. **业务异常**（IllegalArgument/IllegalState）的 message 本来就是写给用户看的，可以透传；
 *    但产生这些消息的地方必须保证不夹带内部信息（openid、session_key、绝对路径等）。
 * 2. **其它异常**一律返回泛化提示，细节只进日志。
 *
 * 同时会给 request 打上「业务错误」标记，供 UsageTrackInterceptor 判断这次调用到底成没成功
 * ——因为响应状态码永远是 200。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 参数校验失败：@Valid / @NotBlank / @Min 等 */
  @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
  public Result<Void> handleValidation(Exception e, HttpServletRequest request) {
    markFailed(request);
    String detail = e.getMessage();
    if (e instanceof MethodArgumentNotValidException manv && manv.getBindingResult().hasFieldErrors()) {
      var fieldError = manv.getBindingResult().getFieldError();
      detail = fieldError == null ? detail
          : fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
    log.info("参数校验失败：{} {}", request.getMethod(), request.getRequestURI());
    return Result.error(400, detail == null ? "参数不合法" : detail);
  }

  /** 方法参数上的约束注解（@RequestParam @Min/@Max 等） */
  @ExceptionHandler(ConstraintViolationException.class)
  public Result<Void> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
    markFailed(request);
    log.info("参数约束失败：{} {}", request.getMethod(), request.getRequestURI());
    return Result.error(400, "参数不合法：" + e.getMessage());
  }

  /** 缺少必填查询参数 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public Result<Void> handleMissingParam(MissingServletRequestParameterException e,
                                         HttpServletRequest request) {
    markFailed(request);
    log.info("缺少参数：{} {}，参数名={}", request.getMethod(), request.getRequestURI(),
        e.getParameterName());
    return Result.error(400, "缺少参数：" + e.getParameterName());
  }

  /** 请求体不是合法 JSON */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Result<Void> handleUnreadable(HttpMessageNotReadableException e,
                                       HttpServletRequest request) {
    markFailed(request);
    log.info("请求体无法解析：{} {}", request.getMethod(), request.getRequestURI());
    return Result.error(400, "请求体格式错误");
  }

  /**
   * Content-Type 不被支持（比如把表单提交打到期望 JSON 的接口上）。
   * 之前这种情况会落到兜底分支，返回"服务器开小差了"，把调用方的错误伪装成服务端故障。
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public Result<Void> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                 HttpServletRequest request) {
    markFailed(request);
    log.info("不支持的 Content-Type：{} {}，type={}", request.getMethod(), request.getRequestURI(),
        e.getContentType());
    return Result.error(415, "请用 application/json 提交");
  }

  /**
   * 数据库唯一键/外键冲突。
   * 典型场景：并发打卡、并发生成同一天的运势 —— 这类冲突对用户来说等价于"已经做过了"，
   * 不应该变成 500。具体是不是幂等场景由业务层决定，这里只保证不再抛裸异常。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public Result<Void> handleDataIntegrity(DataIntegrityViolationException e,
                                          HttpServletRequest request) {
    markFailed(request);
    log.warn("数据库约束冲突：{} {}，原因={}", request.getMethod(), request.getRequestURI(),
        rootMessage(e));
    return Result.error(409, "操作太快啦，请稍后重试");
  }

  /**
   * 业务参数/状态异常：把具体错误信息透给前端（如"不认识的星座"），
   * 这类消息本来就是写给用户看的，不算泄露内部信息。
   *
   * 状态码区分：
   * · IllegalArgumentException = 入参不合法 → 400
   * · IllegalStateException  = 服务端条件不满足（数据源没配、暂时不可用）→ 500
   */
  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public Result<Void> handleBusinessException(RuntimeException e, HttpServletRequest request) {
    markFailed(request);
    log.warn("业务异常：{} {}，原因={}", request.getMethod(), request.getRequestURI(), e.getMessage());
    int code = (e instanceof IllegalArgumentException) ? 400 : 500;
    return Result.error(code, e.getMessage());
  }

  /** 兜底处理所有未捕获异常 */
  @ExceptionHandler(Exception.class)
  public Result<Void> handleException(Exception e, HttpServletRequest request) {
    markFailed(request);
    // 服务端打完整堆栈留痕，方便排查
    log.error("接口异常：{} {}", request.getMethod(), request.getRequestURI(), e);
    // 返回给前端的提示不要太具体，避免泄露内部信息
    return Result.error("服务器开小差了，请稍后再试");
  }

  private static void markFailed(HttpServletRequest request) {
    if (request != null) {
      request.setAttribute(WebAttributes.BUSINESS_ERROR, Boolean.TRUE);
    }
  }

  /** 取最内层异常信息，避免日志里只有一层 Spring 包装 */
  private static String rootMessage(Throwable e) {
    Throwable cur = e;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    return cur.getMessage();
  }
}
