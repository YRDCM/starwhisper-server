package com.starwhisper.server.repository;

import com.starwhisper.server.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  // 按微信 openid 查用户（登录时用）
  Optional<AppUser> findByOpenid(String openid);
}
