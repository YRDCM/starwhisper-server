package com.starwhisper.server.repository;

import com.starwhisper.server.entity.UserHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用户占卜历史数据访问层
 */
public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

  // 某用户的全部历史，最新在前，最多 50 条
  List<UserHistory> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

  // 按类型过滤（TAROT / BAGUA）
  List<UserHistory> findTop50ByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);

  // 「我的」页统计用
  long countByUserId(Long userId);

  long countByUserIdAndType(Long userId, String type);
}
