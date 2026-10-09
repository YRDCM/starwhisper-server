package com.starwhisper.server.repository;

import com.starwhisper.server.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  // 按微信 openid 查用户（登录时用）
  Optional<AppUser> findByOpenid(String openid);

  // 统计：某时刻之后注册的用户数 / 用户列表（后台新增用户统计用）
  long countByCreatedAtGreaterThanEqual(LocalDateTime since);

  List<AppUser> findByCreatedAtGreaterThanEqual(LocalDateTime since);
}
