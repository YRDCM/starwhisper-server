package com.starwhisper.server.repository;

import com.starwhisper.server.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 打卡数据访问层
 */
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

  // 今天打没打过（幂等判断）
  boolean existsByUserIdAndCheckDate(Long userId, LocalDate checkDate);

  // 某用户全部打卡记录，日期新的在前
  List<CheckIn> findByUserIdOrderByCheckDateDesc(Long userId);

  // 累计打卡天数
  long countByUserId(Long userId);
}
