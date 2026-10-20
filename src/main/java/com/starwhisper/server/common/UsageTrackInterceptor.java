package com.starwhisper.server.common;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.UsageEvent;
import com.starwhisper.server.repository.UsageEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 使用埋点拦截器：统一记录关键功能接口的调用（含游客）。
 * 只记 2xx 成功请求；后台/登录接口不记。
 * 注册顺序在 AuthInterceptor 之后（CurrentUser 已挂到 request 上）。
 */
@Component
public class UsageTrackInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(UsageTrackInterceptor.class);

  private final UsageEventRepository repository;

  public UsageTrackInterceptor(UsageEventRepository repository) {
    this.repository = repository;
  }

  /**
   * URI + 方法 → 板块名。只映射"用户主动使用功能"的接口，返回 null 表示不记录。
   * 提出来做成纯静态方法，方便单测覆盖
   */
  public static String moduleOf(String method, String uri) {
    if (uri == null) return null;
    boolean get = "GET".equalsIgnoreCase(method);
    boolean post = "POST".equalsIgnoreCase(method);
    // 星运（今日/近7天/本周都算星运浏览）
    if (get && (uri.equals("/api/fortune/today") || uri.equals("/api/fortune/week") || uri.equals("/api/fortune/weekly"))) return "fortune";
    if (post && uri.equals("/api/match")) return "match";
    if ((post && uri.equals("/api/tarot/draw")) || (get && uri.equals("/api/tarot/daily"))) return "tarot";
    if (post && uri.equals("/api/bagua/cast")) return "bagua";
    if (get && uri.equals("/api/hexagram/today")) return "hexagram";
    if (post && (uri.equals("/api/tarot/ai-reading") || uri.equals("/api/bagua/ai-reading"))) return "ai";
    if (post && uri.equals("/api/checkin")) return "checkin";
    return null;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    try {
      if (response.getStatus() >= 400) return;
      String module = moduleOf(request.getMethod(), request.getRequestURI());
      if (module == null) return;
      AppUser user = CurrentUser.get(request);
      repository.save(new UsageEvent(module, user == null ? null : user.getId(),
          request.getRemoteAddr(), LocalDateTime.now()));
    } catch (Exception e) {
      // 埋点失败绝不能影响业务请求
      log.debug("使用埋点写入失败：{} {}，原因={}", request.getMethod(), request.getRequestURI(), e.getMessage());
    }
  }
}
