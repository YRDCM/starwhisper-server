package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.CheckInService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 每日打卡接口（需要登录；拦截器也会拦，这里的判空是双保险）
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

  private final CheckInService checkInService;

  // 构造注入
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
   * 打卡汇总（v4 契约）：GET /api/checkin/summary?openid=xxx
   * 凭 openid 查询，不需要登录 token（拦截器已对这个路径放行）
   */
  @GetMapping("/summary")
  public Result<Map<String, Object>> summary(@RequestParam(required = false) String openid) {
    return Result.ok0(checkInService.summary(openid));
  }

  /**
   * 未登录的统一返回：和拦截器对 /api/user/** 的处理保持同一个形状
   */
  private Result<Map<String, Object>> needLogin() {
    Result<Map<String, Object>> result = new Result<>();
    result.setCode(401);
    result.setMessage("请先登录");
    return result;
  }
}
