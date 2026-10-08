package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 登录会话：token 就是主键（UUID 去横线），有效期 30 天
 */
@Entity
@Table(name = "user_session")
public class UserSession {

  @Id
  @Column(length = 64)
  private String token;         // UUID 去横线

  private Long userId;

  private LocalDateTime createdAt;
  private LocalDateTime expiresAt; // createdAt + 30 天

  public UserSession() {
  }

  public UserSession(String token, Long userId, LocalDateTime createdAt, LocalDateTime expiresAt) {
    this.token = token;
    this.userId = userId;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }
}
