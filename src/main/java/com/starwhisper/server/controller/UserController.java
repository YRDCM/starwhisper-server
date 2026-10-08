package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.UserHistory;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.UserHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「我的」相关接口：整个 /api/user/** 都被拦截器强制要求登录
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

  private final AppUserRepository appUserRepository;
  private final UserHistoryRepository userHistoryRepository;

  // 构造注入
  public UserController(AppUserRepository appUserRepository, UserHistoryRepository userHistoryRepository) {
    this.appUserRepository = appUserRepository;
    this.userHistoryRepository = userHistoryRepository;
  }

  /**
   * 当前用户信息 + 历史统计：GET /api/user/me
   */
  @GetMapping("/me")
  public Result<Map<String, Object>> me(HttpServletRequest request) {
    AppUser user = CurrentUser.get(request);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", user.getId());
    data.put("nickname", user.getNickname());
    data.put("avatarUrl", user.getAvatarUrl());
    data.put("createdAt", user.getCreatedAt());
    data.put("historyCount", userHistoryRepository.countByUserId(user.getId()));
    data.put("tarotCount", userHistoryRepository.countByUserIdAndType(user.getId(), "TAROT"));
    data.put("baguaCount", userHistoryRepository.countByUserIdAndType(user.getId(), "BAGUA"));
    return Result.success(data);
  }

  /**
   * 更新资料：POST /api/user/profile，body {"nickname": "...", "avatarUrl": "..."}
   * 只更新传了的非空字段
   */
  @PostMapping("/profile")
  public Result<Map<String, Object>> profile(HttpServletRequest request,
                                             @RequestBody Map<String, String> body) {
    AppUser user = CurrentUser.get(request);
    String nickname = body.get("nickname");
    String avatarUrl = body.get("avatarUrl");
    if (StringUtils.hasText(nickname)) {
      user.setNickname(nickname.trim());
    }
    if (StringUtils.hasText(avatarUrl)) {
      user.setAvatarUrl(avatarUrl.trim());
    }
    appUserRepository.save(user);

    return Result.success(Map.of(
        "id", user.getId(),
        "nickname", user.getNickname(),
        "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl()
    ));
  }

  /**
   * 占卜历史：GET /api/user/history?type=TAROT
   * type 可选（TAROT / BAGUA），最新在前，最多 50 条
   */
  @GetMapping("/history")
  public Result<List<UserHistory>> history(HttpServletRequest request,
                                           @RequestParam(required = false) String type) {
    AppUser user = CurrentUser.get(request);
    List<UserHistory> list = StringUtils.hasText(type)
        ? userHistoryRepository.findTop50ByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type.trim().toUpperCase())
        : userHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId());
    return Result.success(list);
  }
}
