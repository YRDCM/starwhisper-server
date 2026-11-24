package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.UserHistory;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.SignRepository;
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

  /** 与 app_user.nickname 的 varchar(64) 对齐 */
  private static final int MAX_NICKNAME_LEN = 32;
  /** 与 app_user.avatar_url 的 varchar(512) 对齐 */
  private static final int MAX_AVATAR_LEN = 512;
  /** 历史记录 type 允许的取值 */
  private static final java.util.Set<String> ALLOWED_HISTORY_TYPES = java.util.Set.of("TAROT", "BAGUA");

  private final AppUserRepository appUserRepository;
  private final UserHistoryRepository userHistoryRepository;
  private final SignRepository signRepository;

  public UserController(AppUserRepository appUserRepository,
                        UserHistoryRepository userHistoryRepository,
                        SignRepository signRepository) {
    this.appUserRepository = appUserRepository;
    this.userHistoryRepository = userHistoryRepository;
    this.signRepository = signRepository;
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
    data.put("signNameEn", user.getSignNameEn());
    data.put("createdAt", user.getCreatedAt());
    data.put("historyCount", userHistoryRepository.countByUserId(user.getId()));
    data.put("tarotCount", userHistoryRepository.countByUserIdAndType(user.getId(), "TAROT"));
    data.put("baguaCount", userHistoryRepository.countByUserIdAndType(user.getId(), "BAGUA"));
    return Result.success(data);
  }

  /**
   * 更新资料：POST /api/user/profile，body {"nickname": "...", "avatarUrl": "..."}
   * 只更新传了的非空字段；昵称全站唯一，撞名直接报错（用户主动改昵称要给明确反馈，不静默加后缀）
   *
   * 之前没有长度校验：昵称超过 64 字符会直接撞数据库列长度抛 500。
   */
  @PostMapping("/profile")
  public Result<Map<String, Object>> profile(HttpServletRequest request,
                                             @RequestBody(required = false) Map<String, String> body) {
    AppUser user = CurrentUser.get(request);
    Map<String, String> payload = (body == null) ? Map.of() : body;

    String nickname = payload.get("nickname");
    String avatarUrl = payload.get("avatarUrl");
    String signNameEn = payload.get("signNameEn");

    // "我的星座"：游客只能存在本地，登录后存到服务端 —— 换手机、清缓存都不会丢。
    // 这是「登录到底有什么用」最直观的一条：你的星运档案跟着账号走。
    if (signNameEn != null) {
      String normalized = signNameEn.trim();
      if (normalized.isEmpty()) {
        user.setSignNameEn(null);
      } else {
        if (normalized.length() > 16) {
          throw new IllegalArgumentException("星座标识不合法");
        }
        String canonical = signRepository.findByNameEnIgnoreCase(normalized)
            .map(sign -> sign.getNameEn())
            .orElseThrow(() -> new IllegalArgumentException("不认识的星座：" + normalized));
        user.setSignNameEn(canonical);
      }
    }

    if (StringUtils.hasText(nickname)) {
      String trimmed = nickname.trim();
      if (trimmed.length() > MAX_NICKNAME_LEN) {
        throw new IllegalArgumentException("昵称最多 " + MAX_NICKNAME_LEN + " 个字");
      }
      if (containsControlChar(trimmed)) {
        throw new IllegalArgumentException("昵称包含不支持的字符");
      }
      // 重名校验：别的用户已占用就拒绝（排除自己）；并发下由 DB 唯一索引兜底
      appUserRepository.findByNickname(trimmed)
          .filter(other -> !other.getId().equals(user.getId()))
          .ifPresent(other -> {
            throw new IllegalArgumentException("昵称已被使用，换一个试试");
          });
      user.setNickname(trimmed);
    }

    if (StringUtils.hasText(avatarUrl)) {
      String trimmed = avatarUrl.trim();
      if (trimmed.length() > MAX_AVATAR_LEN) {
        throw new IllegalArgumentException("头像地址过长");
      }
      if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
        throw new IllegalArgumentException("头像地址必须以 http(s):// 开头");
      }
      user.setAvatarUrl(trimmed);
    }

    appUserRepository.save(user);

    return Result.success(Map.of(
        "id", user.getId(),
        "nickname", user.getNickname(),
        "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
        "signNameEn", user.getSignNameEn() == null ? "" : user.getSignNameEn()
    ));
  }

  /**
   * 占卜历史：GET /api/user/history?type=TAROT
   * type 可选（TAROT / BAGUA），最新在前，最多 50 条。
   * 非白名单值按"全部"处理，避免任意字符串拼进查询。
   */
  @GetMapping("/history")
  public Result<List<UserHistory>> history(HttpServletRequest request,
                                           @RequestParam(required = false) String type) {
    AppUser user = CurrentUser.get(request);
    String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase() : null;
    List<UserHistory> list = (normalized != null && ALLOWED_HISTORY_TYPES.contains(normalized))
        ? userHistoryRepository.findTop50ByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), normalized)
        : userHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId());
    return Result.success(list);
  }

  /** 控制字符（含换行、制表、零宽字符）一律拒绝，避免昵称把 UI 撑坏或在海报上错位 */
  private static boolean containsControlChar(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isISOControl(c) || c == '\u200B' || c == '\u200C' || c == '\u200D') {
        return true;
      }
    }
    return false;
  }
}
