package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 星座相关接口
 */
@RestController
@RequestMapping("/api/signs")
public class SignController {

  @GetMapping("/hello")
  public Result<String> hello() {
    return Result.success("星语系统启动成功 ✨");
  }
}