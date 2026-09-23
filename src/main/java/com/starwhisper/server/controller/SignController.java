package com.starwhisper.server.controller;

import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.service.SignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 星座相关接口
 */
@RestController
@RequestMapping("/api/signs")
public class SignController {

  private final SignService signService;

  // 构造注入：Spring 启动时自动把 SignService 实例传进来
  public SignController(SignService signService) {
    this.signService = signService;
  }

  @GetMapping("/hello")
  public Result<String> hello() {
    return Result.success("星语系统启动成功 ✨");
  }

  @GetMapping("/list")
  public Result<List<Sign>> list() {
    return Result.success(signService.list());
  }
}