package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.AdminStatsService;
import com.starwhisper.server.service.AdminUserService;
import com.starwhisper.server.service.AppConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 后台管理接口（v5：数据总览 / 用户管理 / 参数配置）
 * 鉴权：ADMIN_KEY 环境变量；请求可用 key query 参数或 X-Admin-Key header 二选一。
 * key 不匹配返回 HTTP 403（业务码也用 403，一眼能看懂）；
 * ADMIN_KEY 未配置时整个后台接口不可用（一律 403），宁可关掉也不裸奔。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminStatsService adminStatsService;
  private final AdminUserService adminUserService;
  private final AppConfigService appConfigService;
  private final String adminKey;

  public AdminController(AdminStatsService adminStatsService,
                         AdminUserService adminUserService,
                         AppConfigService appConfigService,
                         @Value("${ADMIN_KEY:}") String adminKey) {
    this.adminStatsService = adminStatsService;
    this.adminUserService = adminUserService;
    this.appConfigService = appConfigService;
    this.adminKey = adminKey;
  }

  /* ===== 统一鉴权 ===== */

  /** key query 参数或 X-Admin-Key header 任一匹配即放行；服务端没配 ADMIN_KEY 时一律拒绝 */
  private boolean authorized(String key, String headerKey) {
    if (!StringUtils.hasText(adminKey)) return false;
    return adminKey.equals(key) || adminKey.equals(headerKey);
  }

  private static <T> ResponseEntity<Result<T>> denied() {
    Result<T> result = new Result<>();
    result.setCode(403);
    result.setMessage("无权访问");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
  }

  /* ===== 模块一：数据总览 ===== */

  /** 运营统计：GET /api/admin/stats?key=xxx */
  @GetMapping("/stats")
  public ResponseEntity<Result<Map<String, Object>>> stats(@RequestParam(required = false) String key,
                                                           @RequestHeader(value = "X-Admin-Key", required = false) String headerKey) {
    if (!authorized(key, headerKey)) return denied();
    return ResponseEntity.ok(Result.ok0(adminStatsService.stats()));
  }

  /* ===== 模块二：用户管理 ===== */

  /** 用户分页列表：GET /api/admin/users?page=0&size=20&keyword=&key=xxx */
  @GetMapping("/users")
  public ResponseEntity<Result<Map<String, Object>>> users(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String key,
                                                           @RequestHeader(value = "X-Admin-Key", required = false) String headerKey) {
    if (!authorized(key, headerKey)) return denied();
    return ResponseEntity.ok(Result.ok0(adminUserService.users(page, size, keyword)));
  }

  /** 用户详情：GET /api/admin/users/{id}?key=xxx（用户不存在 404） */
  @GetMapping("/users/{id}")
  public ResponseEntity<Result<Map<String, Object>>> userDetail(@PathVariable Long id,
                                                                @RequestParam(required = false) String key,
                                                                @RequestHeader(value = "X-Admin-Key", required = false) String headerKey) {
    if (!authorized(key, headerKey)) return denied();
    try {
      return ResponseEntity.ok(Result.ok0(adminUserService.userDetail(id)));
    } catch (IllegalArgumentException e) {
      Result<Map<String, Object>> notFound = new Result<>();
      notFound.setCode(404);
      notFound.setMessage(e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound);
    }
  }

  /**
   * 某用户某天的功能使用明细：GET /api/admin/users/{id}/daily-usage?date=2026-10-19&key=xxx
   * date 可选（默认今天），格式 YYYY-MM-DD；用户不存在 404，日期格式非法 400
   */
  @GetMapping("/users/{id}/daily-usage")
  public ResponseEntity<Result<Map<String, Object>>> dailyUsage(@PathVariable Long id,
                                                                @RequestParam(required = false) String date,
                                                                @RequestParam(required = false) String key,
                                                                @RequestHeader(value = "X-Admin-Key", required = false) String headerKey) {
    if (!authorized(key, headerKey)) return denied();
    LocalDate day = null;
    if (StringUtils.hasText(date)) {
      try {
        day = LocalDate.parse(date.trim());
      } catch (Exception e) {
        Result<Map<String, Object>> badRequest = new Result<>();
        badRequest.setCode(400);
        badRequest.setMessage("日期格式应为 YYYY-MM-DD");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequest);
      }
    }
    try {
      return ResponseEntity.ok(Result.ok0(adminUserService.dailyUsage(id, day)));
    } catch (IllegalArgumentException e) {
      Result<Map<String, Object>> notFound = new Result<>();
      notFound.setCode(404);
      notFound.setMessage(e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound);
    }
  }

  /* ===== 模块三：参数配置 ===== */  /** 全部可编辑配置项：GET /api/admin/config?key=xxx */
  @GetMapping("/config")
  public ResponseEntity<Result<List<Map<String, Object>>>> configList(@RequestParam(required = false) String key,
                                                                      @RequestHeader(value = "X-Admin-Key", required = false) String headerKey) {
    if (!authorized(key, headerKey)) return denied();
    return ResponseEntity.ok(Result.ok0(appConfigService.list()));
  }

  /** 更新配置项：PUT /api/admin/config?key=xxx，body {key, value}；白名单外的 key 返回 400 */
  @PutMapping("/config")
  public ResponseEntity<Result<Map<String, Object>>> updateConfig(@RequestBody Map<String, String> body,
                                                                  @RequestParam(required = false) String key,
                                                                  @RequestHeader(value = "X-Admin-Key", required = false) String headerKey) {
    if (!authorized(key, headerKey)) return denied();
    String configKey = body == null ? null : body.get("key");
    String value = body == null ? null : body.get("value");
    if (!appConfigService.isEditable(configKey)) {
      Result<Map<String, Object>> badRequest = new Result<>();
      badRequest.setCode(400);
      badRequest.setMessage("不支持的配置项：" + configKey);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequest);
    }
    try {
      return ResponseEntity.ok(Result.ok0(appConfigService.update(configKey, value)));
    } catch (IllegalArgumentException e) {
      Result<Map<String, Object>> badRequest = new Result<>();
      badRequest.setCode(400);
      badRequest.setMessage(e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(badRequest);
    }
  }
}
