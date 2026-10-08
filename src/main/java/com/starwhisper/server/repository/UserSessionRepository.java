package com.starwhisper.server.repository;

import com.starwhisper.server.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 登录会话数据访问层（主键就是 token）
 */
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

}
