package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.CheckIn;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.CheckInRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日打卡业务层
 */
@Service
public class CheckInService {

  // 勋章规则（v4 契约）：连续档按历史最大连续天数判定（勋章是成就，断签不回收），累计档按总天数
  private static final List<String[]> BADGES = List.of(
      new String[]{"streak3", "初燃", "连续打卡 3 天", "3", "streak"},
      new String[]{"streak7", "星轨", "连续打卡 7 天", "7", "streak"},
      new String[]{"streak21", "星环", "连续打卡 21 天", "21", "streak"},
      new String[]{"streak30", "恒星", "连续打卡 30 天", "30", "streak"},
      new String[]{"total50", "星尘收藏家", "累计打卡 50 天", "50", "total"},
      new String[]{"total100", "银河旅人", "累计打卡 100 天", "100", "total"});

  private final CheckInRepository checkInRepository;
  private final AppUserRepository appUserRepository;

  public CheckInService(CheckInRepository checkInRepository, AppUserRepository appUserRepository) {
    this.checkInRepository = checkInRepository;
    this.appUserRepository = appUserRepository;
  }

  /**
   * 打卡：幂等，今天打过了就直接返回当前状态，不会重复插行
   */
  public Map<String, Object> checkIn(AppUser user) {
    LocalDate today = LocalDate.now();
    if (!checkInRepository.existsByUserIdAndCheckDate(user.getId(), today)) {
      checkInRepository.save(new CheckIn(user.getId(), today, LocalDateTime.now()));
    }
    return status(user);
  }

  /**
   * 打卡状态：今天是否已打、连续天数、累计天数、最近 14 天记录
   */
  public Map<String, Object> status(AppUser user) {
    LocalDate today = LocalDate.now();
    List<CheckIn> all = checkInRepository.findByUserIdOrderByCheckDateDesc(user.getId());
    Set<LocalDate> dates = all.stream().map(CheckIn::getCheckDate).collect(Collectors.toSet());

    boolean todayDone = dates.contains(today);

    // 连续天数：今天打了从今天往前数；今天没打就从昨天往前数（今天还有机会补上）
    int streak = 0;
    LocalDate cursor = todayDone ? today : today.minusDays(1);
    while (dates.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }

    // 最近 14 天打卡日期（倒序）
    List<LocalDate> recentDates = all.stream()
        .map(CheckIn::getCheckDate)
        .limit(14)
        .toList();

    Map<String, Object> status = new LinkedHashMap<>();
    status.put("todayDone", todayDone);
    status.put("streak", streak);
    status.put("totalDays", checkInRepository.countByUserId(user.getId()));
    status.put("recentDates", recentDates);
    return status;
  }

  /**
   * 打卡汇总（v4 契约，GET /api/checkin/summary?openid=xxx）：
   * currentStreak / maxStreak / totalDays / todayChecked / badges
   * openid 查不到用户时返回全零数据（游客也能看勋章墙，不报错）
   */
  public Map<String, Object> summary(String openid) {
    LocalDate today = LocalDate.now();

    Set<LocalDate> dates = Set.of();
    if (openid != null && !openid.isBlank()) {
      Optional<AppUser> user = appUserRepository.findByOpenid(openid.trim());
      if (user.isPresent()) {
        dates = checkInRepository.findByUserIdOrderByCheckDateDesc(user.get().getId())
            .stream().map(CheckIn::getCheckDate).collect(Collectors.toSet());
      }
    }

    boolean todayChecked = dates.contains(today);

    // 当前连续：今天打了从今天往前数；没打从昨天往前数（今天还有机会补）；昨天也断就是 0
    int currentStreak = 0;
    LocalDate cursor = todayChecked ? today : today.minusDays(1);
    while (dates.contains(cursor)) {
      currentStreak++;
      cursor = cursor.minusDays(1);
    }

    // 历史最大连续：排序后扫描最长连续段
    int maxStreak = 0;
    int run = 0;
    LocalDate prev = null;
    for (LocalDate d : dates.stream().sorted().toList()) {
      run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
      maxStreak = Math.max(maxStreak, run);
      prev = d;
    }

    int totalDays = dates.size();

    List<Map<String, Object>> badges = new ArrayList<>();
    for (String[] badge : BADGES) {
      int threshold = Integer.parseInt(badge[3]);
      // streak 档看历史最大连续（成就制），total 档看累计天数
      int progress = "streak".equals(badge[4]) ? maxStreak : totalDays;
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("code", badge[0]);
      item.put("name", badge[1]);
      item.put("desc", badge[2]);
      item.put("threshold", threshold);
      item.put("unlocked", progress >= threshold);
      badges.add(item);
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("currentStreak", currentStreak);
    summary.put("maxStreak", maxStreak);
    summary.put("totalDays", totalDays);
    summary.put("todayChecked", todayChecked);
    summary.put("badges", badges);
    return summary;
  }
}
