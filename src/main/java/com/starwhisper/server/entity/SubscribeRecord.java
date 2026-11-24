package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 订阅消息登记表：一个用户一个场景一条（唯一约束保证幂等）
 *
 * 微信订阅消息的平台规则：一次授权只能推送一次。
 * 所以这条记录的生命周期是：
 *   用户点「允许」→ ACTIVE（待推送）
 *   推送成功     → CONSUMED（已消耗，想继续收需要用户再次授权）
 *   用户取消     → CANCELLED
 * 前端在推送后应引导用户重新授权（重新调 subscribe 接口会把状态拉回 ACTIVE）。
 */
@Entity
@Table(name = "subscribe_record", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "scene"}))
public class SubscribeRecord {

  /**
   * 场景枚举（先用字符串存，以后加场景直接加常量，不用改表）：
   * DAILY_FORTUNE 每日运势提醒
   * WEEKLY_FORTUNE 每周运势提醒（每周一早上推送）
   */
  public static final String SCENE_DAILY_FORTUNE = "DAILY_FORTUNE";
  public static final String SCENE_WEEKLY_FORTUNE = "WEEKLY_FORTUNE";

  /** 状态：待推送 */
  public static final String STATUS_ACTIVE = "ACTIVE";
  /** 状态：已消耗（推送成功，授权次数用完） */
  public static final String STATUS_CONSUMED = "CONSUMED";
  /** 状态：已取消 */
  public static final String STATUS_CANCELLED = "CANCELLED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  @Column(length = 32)
  private String scene;         // 订阅场景，见 SCENE_* 常量

  @Column(length = 16)
  private String status;        // ACTIVE / CONSUMED / CANCELLED

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public SubscribeRecord() {
  }

  public SubscribeRecord(Long userId, String scene, String status, LocalDateTime now) {
    this.userId = userId;
    this.scene = scene;
    this.status = status;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getScene() {
    return scene;
  }

  public void setScene(String scene) {
    this.scene = scene;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
