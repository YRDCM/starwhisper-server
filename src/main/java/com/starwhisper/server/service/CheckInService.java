package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.CheckIn;
import com.starwhisper.server.repository.CheckInRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日打卡业务层
 */
@Service
public class CheckInService {

  private final CheckInRepository checkInRepository;

  public CheckInService(CheckInRepository checkInRepository) {
    this.checkInRepository = checkInRepository;
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
}
