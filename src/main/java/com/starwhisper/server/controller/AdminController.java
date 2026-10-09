package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.AdminStatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理接口（v4）
 * 用 ADMIN_KEY 环境变量做最简单的鉴权：本地 application-local.properties 配，
 * 服务器 app.env 注入；两边都没配则接口整体不可用（一律 403）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminStatsService adminStatsService;
  private final String adminKey;

  public AdminController(AdminStatsService adminStatsService,
                         @Value("${ADMIN_KEY:}") String adminKey) {
    this.adminStatsService = adminStatsService;
    this.adminKey = adminKey;
  }

  /**
   * 运营统计：GET /api/admin/stats?key=xxx
   * key 不匹配返回 HTTP 403（业务码也用 403，一眼能看懂）
   */
  @GetMapping("/stats")
  public ResponseEntity<Result<Map<String, Object>>> stats(@RequestParam(required = false) String key) {
    if (!StringUtils.hasText(adminKey) || !adminKey.equals(key)) {
      Result<Map<String, Object>> denied = new Result<>();
      denied.setCode(403);
      denied.setMessage("无权访问");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(denied);
    }
    return ResponseEntity.ok(Result.ok0(adminStatsService.stats()));
  }
}
