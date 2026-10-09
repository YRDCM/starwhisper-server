package com.starwhisper.server.repository;

import com.starwhisper.server.entity.UserHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户占卜历史数据访问层
 */
public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

  // 某用户的全部历史，最新在前，最多 50 条
  List<UserHistory> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

  // 按类型过滤（TAROT / BAGUA / MATCH / hexagram）
  List<UserHistory> findTop50ByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);

  // 「我的」页统计用
  long countByUserId(Long userId);

  long countByUserIdAndType(Long userId, String type);

  // 后台统计：某类型的全部记录（牌阵分布聚合用）
  long countByType(String type);

  List<UserHistory> findByType(String type);

  // 后台统计：某时刻之后产生过历史的去重用户（今日活跃口径之一）
  @Query("select distinct h.userId from UserHistory h where h.createdAt >= :since")
  List<Long> findDistinctUserIdsSince(@Param("since") LocalDateTime since);
}
