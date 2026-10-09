package com.starwhisper.server.repository;

import com.starwhisper.server.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  // 统计：某天打卡人数（后台今日打卡数）
  long countByCheckDate(LocalDate checkDate);

  // 统计：某天打卡的用户 id 列表（后台今日活跃口径之一）
  @Query("select c.userId from CheckIn c where c.checkDate = :date")
  List<Long> findUserIdsByCheckDate(@Param("date") LocalDate date);
}
