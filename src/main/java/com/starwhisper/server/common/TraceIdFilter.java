package com.starwhisper.server.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求打一个 traceId，并放进日志 MDC。
 *
 * 解决的问题：一个请求会串起 ShowAPI / Kimi / MySQL 三段外部调用，
 * 之前出问题只能在日志里按时间戳猜。有了 traceId 就能 grep 出一条完整链路。
 *
 * 约定：
 * · 上游（Nginx / 前端）可以带 X-Trace-Id 进来，沿用之；
 * · 否则服务端生成；
 * · 无论哪种都会原样回写响应头，方便前端/网关对账。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Trace-Id";
  private static final String MDC_KEY = "traceId";
  private static final int MAX_LEN = 64;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    String traceId = sanitize(request.getHeader(HEADER));
    if (traceId == null) {
      traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    MDC.put(MDC_KEY, traceId);
    response.setHeader(HEADER, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      // 线程池复用，必须清理，否则下一个请求会串上别人的 traceId
      MDC.remove(MDC_KEY);
    }
  }

  /** 只接受短的可打印字符串，防止外部注入乱码/超长值污染日志 */
  private static String sanitize(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > MAX_LEN) {
      return null;
    }
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
      if (!ok) {
        return null;
      }
    }
    return raw;
  }
}
