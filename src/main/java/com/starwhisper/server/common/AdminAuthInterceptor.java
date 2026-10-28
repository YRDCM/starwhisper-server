package com.starwhisper.server.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后台接口鉴权：保护 /api/admin/**。
 *
 * 规则：
 * 1. **只接受 X-Admin-Key 请求头**，不再支持 ?key= query 参数
 *    （query 会进 Nginx access log、浏览器历史、Referer，等于把密钥写在日志里）
 * 2. 服务端没配 app.admin-key 时一律 403（宁可关掉也不裸奔）
 * 3. 常量时间比较，防时序侧信道
 * 4. 同一 IP 连续失败 N 次锁定 M 分钟，并在日志里留审计记录
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

  static final String HEADER = "X-Admin-Key";

  private final String adminKey;
  private final int maxFailures;
  private final long lockMillis;

  /** IP → 连续失败次数；只在内存里，单机足够 */
  private final Map<String, Failure> failures = new ConcurrentHashMap<>();

  public AdminAuthInterceptor(@Value("${app.admin-key:}") String adminKey,
                              @Value("${app.admin-max-failures:5}") int maxFailures,
                              @Value("${app.admin-lock-minutes:10}") int lockMinutes) {
    this.adminKey = adminKey;
    this.maxFailures = Math.max(1, maxFailures);
    this.lockMillis = Math.max(1, lockMinutes) * 60_000L;
    if (!StringUtils.hasText(adminKey)) {
      log.warn("未配置 app.admin-key：所有 /api/admin/** 接口将返回 403（这是预期行为，不是故障）");
    } else if (adminKey.length() < 16) {
      log.warn("app.admin-key 长度只有 {}，建议换成 32 位以上随机串", adminKey.length());
    }
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    // CORS 预检不带自定义头，直接放行给 Spring 处理
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String ip = clientIp(request);

    if (isLocked(ip)) {
      log.warn("[admin] 鉴权失败次数过多，IP 已被临时锁定：ip={}, uri={}", ip, request.getRequestURI());
      deny(response, "尝试次数过多，请稍后再试");
      return false;
    }

    if (!StringUtils.hasText(adminKey)) {
      deny(response, "后台接口未启用");
      return false;
    }

    String provided = request.getHeader(HEADER);

    // 兼容旧调用方：如果还在用 ?key=，明确拒绝并提示迁移，避免"以为能用"
    String legacyQueryKey = request.getParameter("key");
    if (StringUtils.hasText(legacyQueryKey)) {
      log.warn("[admin] 拒绝使用 query 参数传密钥的请求，请改用 {} 请求头：ip={}, uri={}",
          HEADER, ip, request.getRequestURI());
      deny(response, "请改用 " + HEADER + " 请求头传密钥");
      return false;
    }

    if (!StringUtils.hasText(provided) || !TokenHasher.constantTimeEquals(adminKey, provided)) {
      recordFailure(ip);
      log.warn("[admin] 鉴权失败：ip={}, uri={}, 已带密钥={}", ip, request.getRequestURI(),
          StringUtils.hasText(provided));
      deny(response, "无权访问");
      return false;
    }

    failures.remove(ip);
    log.info("[admin] 鉴权通过：ip={}, uri={}", ip, request.getRequestURI());
    return true;
  }

  private String clientIp(HttpServletRequest request) {
    String ip = request.getRemoteAddr();
    return StringUtils.hasText(ip) ? ip : "unknown";
  }

  private boolean isLocked(String ip) {
    Failure f = failures.get(ip);
    if (f == null) {
      return false;
    }
    if (f.count < maxFailures) {
      return false;
    }
    if (System.currentTimeMillis() - f.lastFailAt > lockMillis) {
      failures.remove(ip);
      return false;
    }
    return true;
  }

  private void recordFailure(String ip) {
    failures.compute(ip, (k, old) -> {
      long now = System.currentTimeMillis();
      if (old == null || now - old.lastFailAt > lockMillis) {
        return new Failure(1, now);
      }
      // 超过阈值后仍继续累加意义不大，封顶避免溢出
      return new Failure(Math.min(old.count + 1, maxFailures * 2), now);
    });
  }

  private void deny(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\",\"data\":null}");
  }

  private record Failure(int count, long lastFailAt) {
  }
}
