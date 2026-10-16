package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.HexagramService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 每日一卦接口（v4）
 */
@RestController
@RequestMapping("/api/hexagram")
public class HexagramController {

  private final HexagramService hexagramService;

  public HexagramController(HexagramService hexagramService) {
    this.hexagramService = hexagramService;
  }

  /**
   * 今日一卦：GET /api/hexagram/today?openid=xxx
   * openid 可选：传了按"日期+用户"确定性出卦并记历史；不传按日期出通用卦
   * 已登录（带 token）优先用 token 身份的真实 openid —— 前端曾误把用户 id 当 openid 传
   */
  @GetMapping("/today")
  public Result<Map<String, Object>> today(@RequestParam(required = false) String openid,
                                           HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    String effectiveOpenid = (user != null && user.getOpenid() != null) ? user.getOpenid() : openid;
    return Result.ok0(hexagramService.today(effectiveOpenid));
  }
}
