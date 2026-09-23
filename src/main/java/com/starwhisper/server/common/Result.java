package com.starwhisper.server.common;

/**
 * 统一返回格式：所有接口都返回 { code, message, data }
 */
public class Result<T> {

  private Integer code;     // 200=成功，500=失败
  private String message;   // 提示信息
  private T data;           // 真正的数据（类型不固定，所以用泛型 T）

  public static <T> Result<T> success(T data) {
    Result<T> result = new Result<>();
    result.code = 200;
    result.message = "success";
    result.data = data;
    return result;
  }

  public static <T> Result<T> error(String message) {
    Result<T> result = new Result<>();
    result.code = 500;
    result.message = message;
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