package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.AdminStatsService;
import com.starwhisper.server.service.AdminUserService;
import com.starwhisper.server.service.AppConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 后台管理接口（数据总览 / 用户管理 / 参数配置）
 *
 * 鉴权：统一由 {@link com.starwhisper.server.common.AdminAuthInterceptor} 在拦截器层完成，
 * **只认 X-Admin-Key 请求头**，不再支持 ?key= query 参数（会泄漏进访问日志）。
 * 因此这里的方法只负责业务，不再做密钥校验。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminStatsService adminStatsService;
  private final AdminUserService adminUserService;
  private final AppConfigService appConfigService;
  private final com.starwhisper.server.repository.FeedbackRepository feedbackRepository;

  public AdminController(AdminStatsService adminStatsService,
                         AdminUserService adminUserService,
                         AppConfigService appConfigService,
                         com.starwhisper.server.repository.FeedbackRepository feedbackRepository) {
    this.adminStatsService = adminStatsService;
    this.adminUserService = adminUserService;
    this.appConfigService = appConfigService;
    this.feedbackRepository = feedbackRepository;
  }

  /* ===== 模块一：数据总览 ===== */

  /** 运营统计：GET /api/admin/stats */
  @GetMapping("/stats")
  public ResponseEntity<Result<Map<String, Object>>> stats() {
    return ResponseEntity.ok(Result.ok0(adminStatsService.stats()));
  }

  /** 运营日报：GET /api/admin/daily-report?date=2026-10-19（date 可选，默认昨天） */
  @GetMapping("/daily-report")
  public ResponseEntity<Result<Map<String, Object>>> dailyReport(
      @RequestParam(required = false) String date) {
    LocalDate day = parseDateOrNull(date);
    if (StringUtils.hasText(date) && day == null) {
      return badRequest("日期格式应为 YYYY-MM-DD");
    }
    return ResponseEntity.ok(Result.ok0(adminStatsService.dailyReport(day)));
  }

  /* ===== 模块二：用户管理 ===== */

  /** 用户分页列表：GET /api/admin/users?page=0&size=20&keyword= */
  @GetMapping("/users")
  public ResponseEntity<Result<Map<String, Object>>> users(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword) {
    return ResponseEntity.ok(Result.ok0(adminUserService.users(page, size, keyword)));
  }

  /** 用户详情：GET /api/admin/users/{id}（用户不存在 404） */
  @GetMapping("/users/{id}")
  public ResponseEntity<Result<Map<String, Object>>> userDetail(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(Result.ok0(adminUserService.userDetail(id)));
    } catch (IllegalArgumentException e) {
      return notFound(e.getMessage());
    }
  }

  /** 某用户某天的功能使用明细：GET /api/admin/users/{id}/daily-usage?date=2026-10-19 */
  @GetMapping("/users/{id}/daily-usage")
  public ResponseEntity<Result<Map<String, Object>>> dailyUsage(
      @PathVariable Long id,
      @RequestParam(required = false) String date) {
    LocalDate day = parseDateOrNull(date);
    if (StringUtils.hasText(date) && day == null) {
      return badRequest("日期格式应为 YYYY-MM-DD");
    }
    try {
      return ResponseEntity.ok(Result.ok0(adminUserService.dailyUsage(id, day)));
    } catch (IllegalArgumentException e) {
      return notFound(e.getMessage());
    }
  }

  /* ===== 模块三：参数配置 ===== */

  /** 全部可编辑配置项：GET /api/admin/config */
  @GetMapping("/config")
  public ResponseEntity<Result<List<Map<String, Object>>>> configList() {
    return ResponseEntity.ok(Result.ok0(appConfigService.list()));
  }

  /** 更新配置项：PUT /api/admin/config，body {key, value}；白名单外的 key 返回 400 */
  @PutMapping("/config")
  public ResponseEntity<Result<Map<String, Object>>> updateConfig(
      @RequestBody(required = false) Map<String, String> body) {
    String configKey = body == null ? null : body.get("key");
    String value = body == null ? null : body.get("value");
    if (!appConfigService.isEditable(configKey)) {
      return badRequest("不支持的配置项：" + configKey);
    }
    try {
      return ResponseEntity.ok(Result.ok0(appConfigService.update(configKey, value)));
    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    }
  }

  /* ===== 模块四：用户反馈 ===== */

  /**
   * 反馈列表（最新在前）：GET /api/admin/feedback?page=0&size=20
   * 游客提交的也在里面（user_id 为空）
   */
  @GetMapping("/feedback")
  public ResponseEntity<Result<Map<String, Object>>> feedback(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(1, size), 100);
    var result = feedbackRepository.findAllByOrderByCreatedAtDesc(
        org.springframework.data.domain.PageRequest.of(safePage, safeSize));
    java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
    for (var fb : result.getContent()) {
      Map<String, Object> item = new java.util.LinkedHashMap<>();
      item.put("id", fb.getId());
      item.put("userId", fb.getUserId());
      item.put("page", fb.getPage());
      item.put("content", fb.getContent());
      item.put("contact", fb.getContact());
      item.put("createdAt", fb.getCreatedAt());
      items.add(item);
    }
    Map<String, Object> data = new java.util.LinkedHashMap<>();
    data.put("total", result.getTotalElements());
    data.put("items", items);
    return ResponseEntity.ok(Result.ok0(data));
  }

  /* ===== 小工具 ===== */

  private static LocalDate parseDateOrNull(String date) {
    if (!StringUtils.hasText(date)) {
      return null;
    }
    try {
      return LocalDate.parse(date.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static <T> ResponseEntity<Result<T>> badRequest(String message) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(messageResult(400, message));
  }

  private static <T> ResponseEntity<Result<T>> notFound(String message) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(messageResult(404, message));
  }

  private static <T> Result<T> messageResult(int code, String message) {
    Result<T> result = new Result<>();
    result.setCode(code);
    result.setMessage(message);
    return result;
  }
}
