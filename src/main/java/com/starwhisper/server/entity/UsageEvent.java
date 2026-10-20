package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 使用埋点流水：谁在什么时候用了哪个板块。
 * 游客（未登录）也记录（userId 为空，按 IP 区分），后台据此汇总游客使用情况。
 * 只记"用了什么"，不记内容细节；IP 仅服务端留存用于去重，不对外展示。
 */
@Entity
@Table(name = "usage_event", indexes = @Index(name = "idx_usage_created", columnList = "created_at"))
public class UsageEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 24, nullable = false)
  private String module;      // fortune / match / tarot / bagua / hexagram / ai / checkin

  private Long userId;        // 登录用户 id；游客为 null

  @Column(length = 64)
  private String ip;          // 访客 IP（游客去重用）

  private LocalDateTime createdAt;

  public UsageEvent() {
  }

  public UsageEvent(String module, Long userId, String ip, LocalDateTime createdAt) {
    this.module = module;
    this.userId = userId;
    this.ip = ip;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getModule() {
    return module;
  }

  public Long getUserId() {
    return userId;
  }

  public String getIp() {
    return ip;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
