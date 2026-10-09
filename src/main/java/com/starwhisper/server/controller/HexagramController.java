package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.HexagramService;
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
   */
  @GetMapping("/today")
  public Result<Map<String, Object>> today(@RequestParam(required = false) String openid) {
    return Result.ok0(hexagramService.today(openid));
  }
}
