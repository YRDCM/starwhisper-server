package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.FortuneVO;
import com.starwhisper.server.dto.WeeklyFortuneVO;
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

  /** 允许查询/生成的日期窗口（天）：防止有人拿 9999-12-31 这类日期把表灌满 */
  private static final int MAX_DATE_WINDOW_DAYS = 365;
  private static final int MAX_GENERATE_WINDOW_DAYS = 7;

  private final FortuneService fortuneService;

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
   * 只允许前后一年内，超范围直接拒绝
   */
  @GetMapping("/date")
  public Result<FortuneVO> byDate(@RequestParam String sign,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    checkWithinWindow(date, MAX_DATE_WINDOW_DAYS);
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
   * 本周运势（ShowAPI week 节点）：GET /api/fortune/weekly?sign=leo
   * 失败直接返回错误，前端显示错误态 —— 周运无本地兜底，不拿编造内容冒充
   */
  @GetMapping("/weekly")
  public Result<WeeklyFortuneVO> weekly(@RequestParam String sign) {
    return Result.success(fortuneService.getWeeklyFortune(sign));
  }

  /**
   * 批量生成某天的 12 星座运势（幂等）：POST /api/fortune/generate?date=2026-09-23
   *
   * ⚠️ 运维接口，需要 X-Admin-Key 请求头（由 AdminAuthInterceptor 在 /api/fortune/generate 上校验）。
   * 之前它是完全公开的：任何人循环调用就能往 fortune_daily 里灌任意日期的数据。
   * 另外日期被限制在前后 7 天内。
   */
  @PostMapping("/generate")
  public Result<Integer> generate(@RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    LocalDate target = (date == null) ? LocalDate.now() : date;
    checkWithinWindow(target, MAX_GENERATE_WINDOW_DAYS);
    return Result.success(fortuneService.generateForDate(target));
  }

  private static void checkWithinWindow(LocalDate date, int windowDays) {
    LocalDate today = LocalDate.now();
    if (date.isBefore(today.minusDays(windowDays)) || date.isAfter(today.plusDays(windowDays))) {
      throw new IllegalArgumentException(
          "日期超出允许范围（前后 " + windowDays + " 天）");
    }
  }
}
