package com.starwhisper.server.service;

import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.CheckInRepository;
import com.starwhisper.server.repository.UserHistoryRepository;
import com.starwhisper.server.repository.UserSessionRepository;
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
  private final ObjectMapper objectMapper;

  public AdminStatsService(AppUserRepository appUserRepository,
                           CheckInRepository checkInRepository,
                           UserHistoryRepository userHistoryRepository,
                           UserSessionRepository userSessionRepository,
                           ObjectMapper objectMapper) {
    this.appUserRepository = appUserRepository;
    this.checkInRepository = checkInRepository;
    this.userHistoryRepository = userHistoryRepository;
    this.userSessionRepository = userSessionRepository;
    this.objectMapper = objectMapper;
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
    data.put("spreadDist", spreadDist());
    data.put("dailyNewUsers", dailyNewUsers(weekAgo));
    return data;
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
