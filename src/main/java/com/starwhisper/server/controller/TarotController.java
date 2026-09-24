package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.DrawnCardVO;
import com.starwhisper.server.dto.TarotCardVO;
import com.starwhisper.server.service.TarotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 塔罗相关接口
 */
@RestController
@RequestMapping("/api/tarot")
public class TarotController {

  private final TarotService tarotService;

  // 构造注入
  public TarotController(TarotService tarotService) {
    this.tarotService = tarotService;
  }

  /**
   * 牌库一览：GET /api/tarot/cards
   */
  @GetMapping("/cards")
  public Result<List<TarotCardVO>> cards() {
    return Result.success(tarotService.listCards());
  }

  /**
   * 抽牌：POST /api/tarot/draw?count=1
   * count=1 单张（无牌阵位置），count=3 三张牌阵（过去/现在/未来）
   */
  @PostMapping("/draw")
  public Result<List<DrawnCardVO>> draw(@RequestParam(defaultValue = "1") int count) {
    return Result.success(tarotService.draw(count));
  }

  /**
   * 每日一牌：GET /api/tarot/daily?sign=aries
   * sign 可选，支持英文名或中文名；不传则是今日通用牌
   */
  @GetMapping("/daily")
  public Result<DrawnCardVO> daily(@RequestParam(required = false) String sign) {
    return Result.success(tarotService.daily(sign));
  }
}
