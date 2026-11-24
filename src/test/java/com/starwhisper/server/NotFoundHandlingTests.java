package com.starwhisper.server;

import com.starwhisper.server.common.GlobalExceptionHandler;
import com.starwhisper.server.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 全局异常处理：不存在的路径不能伪装成服务端故障。
 *
 * 线上出现过 GET /api/config（后端根本没这个路由）落进兜底分支，
 * 打了 ERROR + 50 行堆栈还返回"服务器开小差了"。
 * 这类请求多半来自外部扫描器，噪音会埋掉真正的错误。
 */
class NotFoundHandlingTests {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private MockHttpServletRequest req(String method, String uri) {
    MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
    r.setRequestURI(uri);
    return r;
  }

  @Test
  void 静态资源不存在返回404而不是500() {
    // 真实场景：GET /api/config —— 既没有对应 Controller，也不是静态文件
    MockHttpServletRequest request = req("GET", "/api/config");
    NoResourceFoundException e =
        new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "api/config", null);

    Result<Void> result = handler.handleNotFound(e, request);

    assertEquals(404, result.getCode(), "应该是 404，不是兜底的 500");
    assertEquals("接口不存在", result.getMessage());
    // 仍然要标记为业务失败，避免被 UsageTrackInterceptor 当成一次成功调用计入埋点
    assertEquals(Boolean.TRUE,
        request.getAttribute(com.starwhisper.server.common.WebAttributes.BUSINESS_ERROR));
  }

  @Test
  void 没有匹配的处理器时也返回404() {
    MockHttpServletRequest request = req("POST", "/api/nope");
    NoHandlerFoundException e = new NoHandlerFoundException("POST", "/api/nope", null);

    Result<Void> result = handler.handleNotFound(e, request);

    assertEquals(404, result.getCode());
  }

  @Test
  void 真正的服务端异常仍然是500并保留泛化提示() {
    // 兜底分支不能被这次改动带偏：真正的异常还是要 500 + 泛化文案
    Result<Void> result = handler.handleException(
        new IllegalStateException("内部炸了，这里有敏感细节"), req("GET", "/api/signs/list"));

    assertEquals(500, result.getCode());
    assertNotNull(result.getMessage());
    assertEquals(false, result.getMessage().contains("敏感细节"), "不能把内部信息透给前端");
  }
}
