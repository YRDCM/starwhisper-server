package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.CastVO;
import com.starwhisper.server.dto.HexagramVO;
import com.starwhisper.server.service.BaguaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 易经六十四卦相关接口
 */
@RestController
@RequestMapping("/api/bagua")
public class BaguaController {

  private final BaguaService baguaService;

  // 构造注入
  public BaguaController(BaguaService baguaService) {
    this.baguaService = baguaService;
  }

  /**
   * 卦库一览：GET /api/bagua/hexagrams
   */
  @GetMapping("/hexagrams")
  public Result<List<HexagramVO>> hexagrams() {
    return Result.success(baguaService.listHexagrams());
  }

  /**
   * 三枚铜钱法起卦：POST /api/bagua/cast
   */
  @PostMapping("/cast")
  public Result<CastVO> cast() {
    return Result.success(baguaService.cast());
  }

  /**
   * 每日一卦：GET /api/bagua/daily?sign=aries
   * sign 可选，支持英文名或中文名；不传则是今日通用卦
   */
  @GetMapping("/daily")
  public Result<HexagramVO> daily(@RequestParam(required = false) String sign) {
    return Result.success(baguaService.daily(sign));
  }
}
