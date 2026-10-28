package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.CheckInService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 每日打卡接口（需要登录；拦截器也会拦，这里的判空是双保险）
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

  private final CheckInService checkInService;

  public CheckInController(CheckInService checkInService) {
    this.checkInService = checkInService;
  }

  /**
   * 今日打卡：POST /api/checkin（幂等，重复打不多记）
   */
  @PostMapping
  public Result<Map<String, Object>> checkIn(HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    if (user == null) {
      return needLogin();
    }
    return Result.success(checkInService.checkIn(user));
  }

  /**
   * 打卡状态：GET /api/checkin/status
   */
  @GetMapping("/status")
  public Result<Map<String, Object>> status(HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    if (user == null) {
      return needLogin();
    }
    return Result.success(checkInService.status(user));
  }

  /**
   * 打卡汇总：GET /api/checkin/summary
   *
   * ⚠️ 已移除 openid 入参。原先"未登录时按 openid 查任意用户"是越权读
   * （任何人都能拿别人的 openid 读到他的打卡记录与勋章）。
   * 现在：登录 → 按 token 身份查；未登录 → 返回全零的游客视图。
   */
  @GetMapping("/summary")
  public Result<Map<String, Object>> summary(HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    if (user != null) {
      return Result.ok0(checkInService.summaryByUserId(user.getId()));
    }
    return Result.ok0(checkInService.guestSummary());
  }

  /**
   * 未登录的统一返回：和拦截器对 /api/user/** 的处理保持同一个形状
   */
  private Result<Map<String, Object>> needLogin() {
    return Result.error(401, "请先登录");
  }
}
