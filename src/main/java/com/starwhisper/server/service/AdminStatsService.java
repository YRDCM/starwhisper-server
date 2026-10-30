package com.starwhisper.server.service;

import com.starwhisper.server.entity.CheckIn;
import com.starwhisper.server.entity.SubscribeRecord;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.CheckInRepository;
import com.starwhisper.server.repository.SubscribeRecordRepository;
import com.starwhisper.server.repository.UserHistoryRepository;
import com.starwhisper.server.repository.UserSessionRepository;
import com.starwhisper.server.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 后台管理统计（v4）
 * 数据量小（个人项目），统计直接内存聚合，不建复杂的 SQL 报表
 */
@Service
public class AdminStatsService {

  private static final Logger log = LoggerFactory.getLogger(AdminStatsService.class);

  private final AppUserRepository appUserRepository;
  private final CheckInRepository checkInRepository;
  private final UserHistoryRepository userHistoryRepository;
  private final UserSessionRepository userSessionRepository;
  private final SubscribeRecordRepository subscribeRecordRepository;
  private final AiDailyQuota aiDailyQuota;
  private final UsageEventRepository usageEventRepository;
  private final ObjectMapper objectMapper;

  public AdminStatsService(AppUserRepository appUserRepository,
                           CheckInRepository checkInRepository,
                           UserHistoryRepository userHistoryRepository,
                           UserSessionRepository userSessionRepository,
                           SubscribeRecordRepository subscribeRecordRepository,
                           AiDailyQuota aiDailyQuota,
                           UsageEventRepository usageEventRepository,
                           ObjectMapper objectMapper) {
    this.appUserRepository = appUserRepository;
    this.checkInRepository = checkInRepository;
    this.userHistoryRepository = userHistoryRepository;
    this.userSessionRepository = userSessionRepository;
    this.subscribeRecordRepository = subscribeRecordRepository;
    this.aiDailyQuota = aiDailyQuota;
    this.usageEventRepository = usageEventRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * 运营日报（某一天）：新增/活跃（登录+游客）/板块使用拆分/打卡
   * date 为 null 默认昨天；AI 解读用量只有当天的内存计数，历史日期返回 null
   */
  public Map<String, Object> dailyReport(LocalDate date) {
    LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
    LocalDateTime start = day.atStartOfDay();
    LocalDateTime end = day.plusDays(1).atStartOfDay();

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("date", day.toString());
    data.put("userCount", appUserRepository.count()); // 截至当前的总用户（参考值）
    data.put("newUsers", appUserRepository.countByCreatedAtBetween(start, end));
    data.put("activeUsers", usageEventRepository.countDistinctUsersBetween(start, end));
    data.put("guestActive", usageEventRepository.countDistinctGuestIpsBetween(start, end));
    data.put("checkins", checkInRepository.countByCheckDate(day));
    data.put("aiUsed", day.equals(LocalDate.now()) ? aiDailyQuota.todayUsed() : null); // 历史无记录
    data.put("moduleSplit", todayModuleSplit(day, start));
    return data;
  }

  /**
   * 运营统计总览
   * todayActive 口径：今天有过登录（新建会话）、打卡、或产生占卜历史的去重用户数
   */
  public Map<String, Object> stats() {
    LocalDate today = LocalDate.now();
    LocalDateTime dayStart = today.atStartOfDay();
    LocalDateTime weekAgo = today.minusDays(6).atStartOfDay(); // 含今天共 7 天

    // 今日活跃：登录 + 打卡 + 占卜历史三个来源的去重并集
    Set<Long> activeUsers = new HashSet<>();
    activeUsers.addAll(userSessionRepository.findDistinctUserIdsSince(dayStart));
    activeUsers.addAll(userHistoryRepository.findDistinctUserIdsSince(dayStart));
    activeUsers.addAll(checkInRepository.findUserIdsByCheckDate(today));

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userCount", appUserRepository.count());
    data.put("todayNewUsers", appUserRepository.countByCreatedAtGreaterThanEqual(dayStart));
    data.put("todayActive", activeUsers.size());
    data.put("checkinTotal", checkInRepository.count());
    data.put("todayCheckins", checkInRepository.countByCheckDate(today));
    data.put("tarotTotal", userHistoryRepository.countByType("TAROT"));
    data.put("baguaTotal", userHistoryRepository.countByType("BAGUA"));
    data.put("matchTotal", userHistoryRepository.countByType("MATCH"));
    data.put("spreadDist", spreadDist());
    data.put("dailyNewUsers", dailyNewUsers(weekAgo));
    data.put("dailyActive", dailyActive(today));
    // AI 限额：来自 AiDailyQuota 的运行时状态（限额值跟随 app_config 动态配置）
    data.put("aiTodayUsed", aiDailyQuota.todayUsed());
    data.put("aiGlobalLimit", aiDailyQuota.globalLimit());
    data.put("aiGlobalRemaining", aiDailyQuota.globalRemaining());
    data.put("subscribeActive", subscribeRecordRepository.countByStatus(SubscribeRecord.STATUS_ACTIVE));
    // 板块使用次数：有行为记录的模块全量统计；星运浏览未埋点，返回 null 由前端标注
    data.put("moduleUsage", moduleUsage());
    // 今日板块使用（登录 vs 游客拆分）+ 今日活跃游客/用户 IP 数（按 IP 去重）；2026-10-20 起有埋点数据
    data.put("todayModuleSplit", todayModuleSplit(today, dayStart));
    data.put("todayGuestActive", usageEventRepository.countDistinctGuestIpsSince(dayStart));
    data.put("todayUserIps", usageEventRepository.countDistinctUserIpsSince(dayStart));
    return data;
  }

  /** 今日板块使用：登录用户次数 / 游客次数（埋点表 usage_event 按天聚合） */
  private List<Map<String, Object>> todayModuleSplit(LocalDate today, LocalDateTime dayStart) {
    LocalDateTime end = today.plusDays(1).atStartOfDay();
    String[][] modules = {
        {"fortune", "星运浏览"}, {"tarot", "塔罗抽牌"}, {"match", "星座配对"},
        // 八卦/每日一卦已按平台要求下线，这里保留计数并带 offline 标记，避免大盘上看不出它已经不在服务
        {"bagua", "八卦"}, {"hexagram", "每日一卦"},
        {"checkin", "每日打卡"}, {"ai", "AI 解读"}
    };
    List<Map<String, Object>> list = new ArrayList<>();
    for (String[] m : modules) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("key", m[0]);
      item.put("name", m[1]);
      item.put("offline", OFFLINE_MODULES.contains(m[0]));
      item.put("user", usageEventRepository.countByModuleAndUserIdIsNotNullAndCreatedAtBetween(m[0], dayStart, end));
      item.put("guest", usageEventRepository.countByModuleAndUserIdIsNullAndCreatedAtBetween(m[0], dayStart, end));
      list.add(item);
    }
    return list;
  }

  /**
   * 已按平台要求下线、但历史数据仍保留用于对照的板块。
   * 名字里不再带「（已下线）」——那会让 88px 宽的标签列折成两行；
   * 改由 offline 标记交给前端用灰色 + 小标签表达。
   */
  private static final java.util.Set<String> OFFLINE_MODULES = java.util.Set.of("bagua", "hexagram");

  /**
   * 板块使用次数（全量累计）：
   * 塔罗/起卦/配对取自 user_history 的 type 计数（每日一卦的 type 是小写 hexagram，历史遗留），
   * 打卡取 check_in 总数；星运页浏览没有埋点记录，count 为 null 表示「未统计」而不是 0
   */
  private List<Map<String, Object>> moduleUsage() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(moduleItem("tarot", "塔罗抽牌", userHistoryRepository.countByType("TAROT")));
    list.add(moduleItem("match", "星座配对", userHistoryRepository.countByType("MATCH")));
    // 已下线板块：保留历史累计用于对照，用 offline 标记，避免被误认为是现役功能
    list.add(moduleItem("bagua", "八卦", userHistoryRepository.countByType("BAGUA")));
    list.add(moduleItem("hexagram", "每日一卦", userHistoryRepository.countByType("hexagram")));
    list.add(moduleItem("checkin", "每日打卡", checkInRepository.count()));
    list.add(moduleItem("fortune", "星运浏览", null)); // 未埋点
    return list;
  }

  private Map<String, Object> moduleItem(String key, String name, Long count) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", key);
    item.put("name", name);
    item.put("count", count);
    item.put("offline", OFFLINE_MODULES.contains(key));
    return item;
  }

  /**
   * 近 7 天每日活跃（含今天，日期升序，没人的日子补 0）
   * 口径与 todayActive 一致：当天登录（新建会话）+ 打卡 + 产生占卜历史的去重用户数
   */
  private List<Map<String, Object>> dailyActive(LocalDate today) {
    Map<LocalDate, Set<Long>> buckets = new TreeMap<>();
    for (int i = 6; i >= 0; i--) {
      buckets.put(today.minusDays(i), new HashSet<>());
    }
    LocalDateTime weekAgo = today.minusDays(6).atStartOfDay();
    userSessionRepository.findByCreatedAtGreaterThanEqual(weekAgo)
        .forEach(s -> buckets.computeIfAbsent(s.getCreatedAt().toLocalDate(), d -> new HashSet<>()).add(s.getUserId()));
    userHistoryRepository.findByCreatedAtGreaterThanEqual(weekAgo)
        .forEach(h -> buckets.computeIfAbsent(h.getCreatedAt().toLocalDate(), d -> new HashSet<>()).add(h.getUserId()));
    checkInRepository.findByCheckDateGreaterThanEqual(today.minusDays(6))
        .forEach(c -> buckets.computeIfAbsent(c.getCheckDate(), d -> new HashSet<>()).add(c.getUserId()));
    List<Map<String, Object>> daily = new ArrayList<>();
    buckets.forEach((date, users) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", date.toString());
      item.put("count", users.size());
      daily.add(item);
    });
    return daily;
  }

  /**
   * 塔罗牌阵分布：解析 user_history 里 TAROT 记录的 detail JSON，按 spread 计数
   * 解析失败的旧数据归入 unknown，不让脏数据拖垮统计
   */
  private List<Map<String, Object>> spreadDist() {
    Map<String, Long> counter = new TreeMap<>();
    userHistoryRepository.findByType("TAROT").forEach(history -> {
      String spread = "unknown";
      try {
        var node = objectMapper.readTree(history.getDetail());
        if (node.has("spread") && node.get("spread").isTextual()) {
          spread = node.get("spread").asText();
        }
      } catch (Exception e) {
        log.debug("历史 detail 解析失败，计入 unknown：id={}", history.getId());
      }
      counter.merge(spread, 1L, Long::sum);
    });
    List<Map<String, Object>> dist = new ArrayList<>();
    counter.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(entry -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("spread", entry.getKey());
          item.put("count", entry.getValue());
          dist.add(item);
        });
    return dist;
  }

  /**
   * 近 7 天每日新增用户（含今天，日期升序，没人的日子补 0）
   */
  private List<Map<String, Object>> dailyNewUsers(LocalDateTime since) {
    Map<LocalDate, Long> counter = new TreeMap<>();
    for (int i = 0; i < 7; i++) {
      counter.put(since.toLocalDate().plusDays(i), 0L);
    }
    appUserRepository.findByCreatedAtGreaterThanEqual(since)
        .forEach(user -> counter.merge(user.getCreatedAt().toLocalDate(), 1L, Long::sum));
    List<Map<String, Object>> daily = new ArrayList<>();
    counter.forEach((date, count) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", date.toString());
      item.put("count", count);
      daily.add(item);
    });
    return daily;
  }
}
