package com.starwhisper.server.repository;

import com.starwhisper.server.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  // 按微信 openid 查用户（登录时用）
  Optional<AppUser> findByOpenid(String openid);

  // 昵称唯一性校验（重名检查用；昵称有唯一索引兜底）
  Optional<AppUser> findByNickname(String nickname);

  // 统计：某时刻之后注册的用户数 / 用户列表（后台新增用户统计用）
  long countByCreatedAtGreaterThanEqual(LocalDateTime since);

  // 统计：某天注册的用户数（运营日报用）
  long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

  // 某天注册的用户 id 列表（留存计算用：注册日 → 后续活跃比对）
  @Query("select u.id from AppUser u where u.createdAt between :start and :end")
  List<Long> findIdsByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  List<AppUser> findByCreatedAtGreaterThanEqual(LocalDateTime since);

  // 后台用户管理：昵称模糊分页（大小写不敏感）
  Page<AppUser> findByNicknameContainingIgnoreCase(String nickname, Pageable pageable);
}
