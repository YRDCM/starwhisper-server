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
}
