package com.starwhisper.server.repository;

import com.starwhisper.server.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录会话数据访问层（主键就是 token）
 */
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

  // 后台统计：某时刻之后登录过（新建会话）的去重用户（今日活跃口径之一）
  @Query("select distinct s.userId from UserSession s where s.createdAt >= :since")
  List<Long> findDistinctUserIdsSince(@Param("since") LocalDateTime since);

  // 后台统计：某时刻之后的全部会话（近 7 天每日活跃按天分桶用）
  List<UserSession> findByCreatedAtGreaterThanEqual(LocalDateTime since);

  // 后台用户管理：一批用户各自的最近登录时间（userId -> max(createdAt)）
  @Query("select s.userId, max(s.createdAt) from UserSession s where s.userId in :ids group by s.userId")
  List<Object[]> findLastActiveAtByUserIdIn(@Param("ids") List<Long> ids);

  // 后台「某天用了哪些功能」：某用户一天内的登录次数
  long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
