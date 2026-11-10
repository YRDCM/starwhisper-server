package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.HexagramService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 每日一卦接口（v4）
 *
 * ⚠️ 合规提示：该板块此前已被平台要求下架，前端入口与页面已移除。
 * 后端接口暂时保留（旧版本小程序仍可能调用），是否彻底关闭由运营决定。
 */
@RestController
@RequestMapping("/api/hexagram")
public class HexagramController {

  private final HexagramService hexagramService;

  public HexagramController(HexagramService hexagramService) {
    this.hexagramService = hexagramService;
  }

  /**
   * 今日一卦：GET /api/hexagram/today
   *
   * ⚠️ 已移除 openid 入参。原先未登录时可以传别人的 openid，
   * 导致请求方能把一条历史记录写进任意用户的账号里（越权写，
   * 而且每次 GET 都会落一行，可以拿来灌表）。
   * 现在只认 token 身份：登录 → 按本人出卦并记历史；未登录 → 出通用卦、不记历史。
   */
  @GetMapping("/today")
  public Result<Map<String, Object>> today(HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    String openid = (user == null) ? null : user.getOpenid();
    return Result.ok0(hexagramService.today(openid));
  }
}
