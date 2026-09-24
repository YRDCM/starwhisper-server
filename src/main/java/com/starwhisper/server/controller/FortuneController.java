package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.FortuneVO;
import com.starwhisper.server.service.FortuneService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日运势相关接口
 * sign 参数支持英文名（aries，忽略大小写）或中文名（白羊座）
 */
@RestController
@RequestMapping("/api/fortune")
public class FortuneController {

  private final FortuneService fortuneService;

  // 构造注入
  public FortuneController(FortuneService fortuneService) {
    this.fortuneService = fortuneService;
  }

  /**
   * 今日运势：GET /api/fortune/today?sign=aries
   */
  @GetMapping("/today")
  public Result<FortuneVO> today(@RequestParam String sign) {
    return Result.success(fortuneService.getFortune(sign, LocalDate.now()));
  }

  /**
   * 指定日期运势：GET /api/fortune/date?sign=aries&date=2026-09-23
   */
  @GetMapping("/date")
  public Result<FortuneVO> byDate(@RequestParam String sign,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return Result.success(fortuneService.getFortune(sign, date));
  }

  /**
   * 最近 7 天运势：GET /api/fortune/week?sign=aries
   */
  @GetMapping("/week")
  public Result<List<FortuneVO>> week(@RequestParam String sign) {
    return Result.success(fortuneService.getWeek(sign));
  }

  /**
   * 批量生成某天的 12 星座运势（幂等）：POST /api/fortune/generate?date=2026-09-23
   * 不传 date 默认今天，返回本次新增的条数
   */
  @PostMapping("/generate")
  public Result<Integer> generate(@RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    LocalDate target = (date == null) ? LocalDate.now() : date;
    return Result.success(fortuneService.generateForDate(target));
  }
}
