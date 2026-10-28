package com.starwhisper.server.common;

/**
 * 统一返回格式：所有接口都返回 { code, message, data }
 *
 * code 约定：
 * · 0    新版契约的成功码（打卡 summary / 每日一卦 / 后台统计）
 * · 200  旧版成功码
 * · 4xx/5xx 失败：401 未登录、400 参数错、403 无权限、404 不存在、409 冲突、500 服务端错误
 *
 * 注意：HTTP 状态码一律是 200（老前端按 body.code 判断），
 * 所以「请求是否成功」不能用 HTTP status 判断 —— 见 WebAttributes.BUSINESS_ERROR。
 */
public class Result<T> {

  private Integer code;
  private String message;
  private T data;

  public static <T> Result<T> success(T data) {
    return build(200, "success", data);
  }

  /** 新版契约成功码 0 */
  public static <T> Result<T> ok0(T data) {
    return build(0, "success", data);
  }

  public static <T> Result<T> error(String message) {
    return build(500, message, null);
  }

  public static <T> Result<T> error(int code, String message) {
    return build(code, message, null);
  }

  private static <T> Result<T> build(int code, String message, T data) {
    Result<T> result = new Result<>();
    result.code = code;
    result.message = message;
    result.data = data;
    return result;
  }

  // getter/setter（序列化成 JSON 必须有）
  public Integer getCode() { return code; }
  public void setCode(Integer code) { this.code = code; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public T getData() { return data; }
  public void setData(T data) { this.data = data; }
}
