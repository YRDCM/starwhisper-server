package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.CheckIn;
import com.starwhisper.server.entity.UserHistory;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.CheckInRepository;
import com.starwhisper.server.repository.UserHistoryRepository;
import com.starwhisper.server.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 后台用户管理（v5）：分页列表 + 用户详情
 * 个人项目数据量小，打卡连续天数在内存里按与 CheckInService 相同的口径计算
 */
@Service
public class AdminUserService {

  private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

  /** 每页上限：防止 size 传一个巨大值把库拖垮 */
  public static final int MAX_PAGE_SIZE = 50;
  /** 详情页最近历史条数 */
  public static final int RECENT_HISTORY_LIMIT = 20;
  /** detail JSON 兜底摘要的截断长度 */
  private static final int TITLE_FALLBACK_LEN = 60;

  private final AppUserRepository appUserRepository;
  private final CheckInRepository checkInRepository;
  private final UserHistoryRepository userHistoryRepository;
  private final UserSessionRepository userSessionRepository;
  private final CheckInService checkInService;
  private final ObjectMapper objectMapper;

  public AdminUserService(AppUserRepository appUserRepository,
                          CheckInRepository checkInRepository,
                          UserHistoryRepository userHistoryRepository,
                          UserSessionRepository userSessionRepository,
                          CheckInService checkInService,
                          ObjectMapper objectMapper) {
    this.appUserRepository = appUserRepository;
    this.checkInRepository = checkInRepository;
    this.userHistoryRepository = userHistoryRepository;
    this.userSessionRepository = userSessionRepository;
    this.checkInService = checkInService;
    this.objectMapper = objectMapper;
  }

  /**
   * 分页用户列表：{list, total, page, size}，默认 id 倒序（新用户在前）
   * keyword 非空时按昵称模糊匹配（忽略大小写）
   */
  public Map<String, Object> users(int page, int size, String keyword) {
    int safePage = Math.max(0, page);
    int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));

    Page<AppUser> result = StringUtils.hasText(keyword)
        ? appUserRepository.findByNicknameContainingIgnoreCase(keyword.trim(), pageable)
        : appUserRepository.findAll(pageable);

    List<AppUser> users = result.getContent();
    List<Long> ids = users.stream().map(AppUser::getId).toList();

    // 打卡聚合：一次查询取回本页用户的全部打卡记录，内存分组算累计/连续
    Map<Long, List<LocalDate>> checkinDates = new HashMap<>();
    if (!ids.isEmpty()) {
      for (CheckIn checkIn : checkInRepository.findByUserIdInOrderByCheckDateDesc(ids)) {
        checkinDates.computeIfAbsent(checkIn.getUserId(), k -> new ArrayList<>()).add(checkIn.getCheckDate());
      }
    }

    // 最近活跃：user_session 里每个用户最近一次的 created_at
    Map<Long, LocalDateTime> lastActive = new HashMap<>();
    if (!ids.isEmpty()) {
      for (Object[] row : userSessionRepository.findLastActiveAtByUserIdIn(ids)) {
        lastActive.put((Long) row[0], (LocalDateTime) row[1]);
      }
    }

    List<Map<String, Object>> list = new ArrayList<>();
    for (AppUser user : users) {
      List<LocalDate> dates = checkinDates.getOrDefault(user.getId(), List.of());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", user.getId());
      item.put("nickname", user.getNickname());
      item.put("avatarUrl", user.getAvatarUrl());
      item.put("signNameEn", user.getSignNameEn());
      item.put("createdAt", user.getCreatedAt());
      item.put("totalCheckins", dates.size());
      item.put("currentStreak", currentStreak(dates));
      item.put("lastActiveAt", lastActive.get(user.getId()));
      list.add(item);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("list", list);
    data.put("total", result.getTotalElements());
    data.put("page", safePage);
    data.put("size", safeSize);
    return data;
  }

  /**
   * 用户详情：基础信息 + 打卡汇总（复用 CheckInService，口径与小程序端一致）
   * + 最近 20 条历史（title 缺失时从 detail JSON 提取摘要兜底）
   * 用户不存在抛 IllegalArgumentException，控制器转 404
   */
  public Map<String, Object> userDetail(Long id) {
    AppUser user = appUserRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在：" + id));

    Map<String, Object> base = new LinkedHashMap<>();
    base.put("id", user.getId());
    base.put("nickname", user.getNickname());
    base.put("avatarUrl", user.getAvatarUrl());
    base.put("signNameEn", user.getSignNameEn());
    base.put("createdAt", user.getCreatedAt());
    base.put("lastLoginAt", user.getLastLoginAt());

    List<Map<String, Object>> histories = new ArrayList<>();
    userHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(id).forEach(history -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("type", history.getType());
      item.put("title", historyTitle(history));
      item.put("createdAt", history.getCreatedAt());
      histories.add(item);
    });

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("user", base);
    data.put("checkin", checkInService.summaryByUserId(id));
    data.put("histories", histories);
    return data;
  }

  /**
   * 某用户某天的功能使用明细：
   * modules = 各板块当日次数（登录/打卡/塔罗/配对/起卦/每日一卦；星运浏览和 AI 解读未按用户落库，标 null 表示未统计）
   * events  = 当日行为时间线（从 user_history + 打卡还原，时间升序）
   * date 为 null 时默认今天；用户不存在抛 IllegalArgumentException
   */
  public Map<String, Object> dailyUsage(Long userId, LocalDate date) {
    if (!appUserRepository.existsById(userId)) {
      throw new IllegalArgumentException("用户不存在：" + userId);
    }
    LocalDate day = date == null ? LocalDate.now() : date;
    LocalDateTime start = day.atStartOfDay();
    LocalDateTime end = day.plusDays(1).atStartOfDay();

    List<UserHistory> histories = userHistoryRepository
        .findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(userId, start, end);
    long logins = userSessionRepository.countByUserIdAndCreatedAtBetween(userId, start, end);
    long checkins = checkInRepository.countByUserIdAndCheckDate(userId, day);

    // 板块计数：user_history 按 type 分组（每日一卦历史 type 是小写 hexagram，兼容）
    Map<String, Long> byType = histories.stream()
        .collect(Collectors.groupingBy(h -> h.getType() == null ? "unknown" : h.getType(), Collectors.counting()));

    List<Map<String, Object>> modules = new ArrayList<>();
    modules.add(usageItem("login", "登录", logins));
    modules.add(usageItem("checkin", "每日打卡", checkins));
    modules.add(usageItem("tarot", "塔罗抽牌", byType.getOrDefault("TAROT", 0L)));
    modules.add(usageItem("match", "星座配对", byType.getOrDefault("MATCH", 0L)));
    modules.add(usageItem("bagua", "八卦（已下线）", byType.getOrDefault("BAGUA", 0L)));
    modules.add(usageItem("hexagram", "每日一卦（已下线）",
        byType.getOrDefault("hexagram", 0L) + byType.getOrDefault("HEXAGRAM", 0L)));
    modules.add(usageItem("fortune", "星运浏览", null)); // 未埋点
    modules.add(usageItem("ai", "AI 解读", null));       // 限额按 IP 记内存，未按用户落库

    // 当日时间线
    List<Map<String, Object>> events = new ArrayList<>();
    for (UserHistory history : histories) {
      Map<String, Object> event = new LinkedHashMap<>();
      event.put("time", history.getCreatedAt().toLocalTime().toString());
      event.put("type", history.getType());
      event.put("title", historyTitle(history));
      events.add(event);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userId", userId);
    data.put("date", day.toString());
    data.put("modules", modules);
    data.put("events", events);
    return data;
  }

  private Map<String, Object> usageItem(String key, String name, Long count) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", key);
    item.put("name", name);
    item.put("count", count); // null = 该板块未按用户统计，前端显示「未统计」而非 0
    return item;
  }

  /**
   * 当前连续打卡天数，口径与 CheckInService.buildSummary 一致：
   * 今天打了从今天往前数；没打从昨天往前数（今天还有机会补）；昨天也断就是 0
   */
  static int currentStreak(List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) return 0;
    Set<LocalDate> set = new TreeSet<>(dates);
    LocalDate today = LocalDate.now();
    LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
    int streak = 0;
    while (set.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }

  /**
   * 历史标题兜底：title 为空时从 detail JSON 提取关键信息截断，
   * 解析失败就裸截断原文，绝不让一条脏数据拖垮详情接口
   */
  String historyTitle(UserHistory history) {
    if (StringUtils.hasText(history.getTitle())) {
      return history.getTitle();
    }
    String detail = history.getDetail();
    if (!StringUtils.hasText(detail)) {
      return "";
    }
    try {
      var node = objectMapper.readTree(detail);
      // 常见结构：塔罗 {spread, cards} / 配对 {star1, star2} / 起卦 {primary}，取第一个文本字段
      for (var entry : node.properties()) {
        if (entry.getValue().isTextual() && StringUtils.hasText(entry.getValue().asText())) {
          return truncate(entry.getValue().asText());
        }
      }
    } catch (Exception e) {
      log.debug("历史 detail 解析失败，裸截断兜底：id={}", history.getId());
    }
    return truncate(detail);
  }

  private static String truncate(String text) {
    String clean = text.replaceAll("\\s+", " ").trim();
    return clean.length() <= TITLE_FALLBACK_LEN ? clean : clean.substring(0, TITLE_FALLBACK_LEN) + "…";
  }
}
