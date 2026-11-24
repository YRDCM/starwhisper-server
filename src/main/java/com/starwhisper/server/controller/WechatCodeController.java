package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.service.WechatMpCodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序码接口（海报用）：GET /api/wechat/mpcode?scene=leo
 * 游客可用（海报谁都能生成），返回 base64 PNG
 */
@RestController
@RequestMapping("/api/wechat")
public class WechatCodeController {

  private final WechatMpCodeService mpCodeService;

  public WechatCodeController(WechatMpCodeService mpCodeService) {
    this.mpCodeService = mpCodeService;
  }

  @GetMapping("/mpcode")
  public Result<Map<String, String>> mpcode(@RequestParam(defaultValue = "") String scene) {
    return Result.success(Map.of("image", mpCodeService.mpCodeBase64(scene)));
  }
}
