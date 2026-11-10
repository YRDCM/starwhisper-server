package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 订阅消息接口：整个 /api/notify/** 都被拦截器强制要求登录
 *
 * 背景：微信订阅消息「一次授权只能推一次」，所以 subscribe 是登记一笔待推送额度，
 * 推送成功后额度即消耗，前端会引导用户再次授权（见 DailyFortuneNotifyJob 注释）。
 */
@RestController
@RequestMapping("/api/notify")
public class NotifyController {

  private final NotifyService notifyService;

  public NotifyController(NotifyService notifyService) {
    this.notifyService = notifyService;
  }

  /**
   * 订阅：POST /api/notify/subscribe，body {"scene": "DAILY_FORTUNE", "sign": "aries"(可选)}
   * 用户在小程序 wx.requestSubscribeMessage 点了「允许」后调用
   */
  @PostMapping("/subscribe")
  public Result<Map<String, Object>> subscribe(HttpServletRequest request,
                                               @RequestBody Map<String, String> body) {
    AppUser user = CurrentUser.get(request);
    notifyService.subscribe(user, body.get("scene"), body.get("sign"));
    return Result.success(Map.of("scene", body.get("scene").trim().toUpperCase(), "subscribed", true));
  }

  /**
   * 退订：POST /api/notify/unsubscribe，body {"scene": "DAILY_FORTUNE"}
   */
  @PostMapping("/unsubscribe")
  public Result<Map<String, Object>> unsubscribe(HttpServletRequest request,
                                                 @RequestBody Map<String, String> body) {
    AppUser user = CurrentUser.get(request);
    notifyService.unsubscribe(user, body.get("scene"));
    return Result.success(Map.of("scene", body.get("scene").trim().toUpperCase(), "subscribed", false));
  }

  /**
   * 订阅状态：GET /api/notify/status
   * → { scenes: ["DAILY_FORTUNE", ...], consumed: ["DAILY_FORTUNE", ...] }
   *
   * scenes   = 还有待推送额度的（ACTIVE）
   * consumed = 已经推送过、额度用完的
   * 两者分开是为了让前端能区分「从没订阅」和「刚推完，需要再次授权」——
   * 后者如果也显示成「未订阅」，用户会以为推送没生效。
   */
  @GetMapping("/status")
  public Result<Map<String, Object>> status(HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    return Result.success(Map.of(
        "scenes", notifyService.activeScenes(user.getId()),
        "consumed", notifyService.consumedScenes(user.getId())));
  }
}
