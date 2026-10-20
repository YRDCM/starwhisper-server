package com.starwhisper.server.repository;

import com.starwhisper.server.entity.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 使用埋点数据访问
 */
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

  // 某模块某时段次数（区分登录/游客）
  long countByModuleAndUserIdIsNullAndCreatedAtBetween(String module, LocalDateTime start, LocalDateTime end);

  long countByModuleAndUserIdIsNotNullAndCreatedAtBetween(String module, LocalDateTime start, LocalDateTime end);

  // 今日活跃游客数（按 IP 去重）
  @Query("select count(distinct e.ip) from UsageEvent e where e.userId is null and e.createdAt >= :since")
  long countDistinctGuestIpsSince(@Param("since") LocalDateTime since);

  // 近 N 天逐日活跃游客数（后台趋势图用）
  @Query("select count(distinct e.ip) from UsageEvent e where e.userId is null and e.createdAt between :start and :end")
  long countDistinctGuestIpsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  // 某日活跃登录用户数（埋点行为去重，运营日报用）
  @Query("select count(distinct e.userId) from UsageEvent e where e.userId is not null and e.createdAt between :start and :end")
  long countDistinctUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  // 某模块某时段次数（不区分身份，总览模块使用次数用）
  long countByModuleAndCreatedAtGreaterThanEqual(String module, LocalDateTime since);

  // 历史累计（含游客）：某模块总数
  long countByModule(String module);

  // 游客今日事件流（后台明细查看）
  List<UsageEvent> findTop100ByUserIdIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime since);
}
