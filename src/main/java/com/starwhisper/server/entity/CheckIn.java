package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日打卡：一个用户一天最多一条（唯一约束保证幂等）
 */
@Entity
@Table(name = "check_in", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "check_date"}))
public class CheckIn {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  private LocalDate checkDate;  // 打卡日期（按自然日）

  private LocalDateTime createdAt;

  public CheckIn() {
  }

  public CheckIn(Long userId, LocalDate checkDate, LocalDateTime createdAt) {
    this.userId = userId;
    this.checkDate = checkDate;
    this.createdAt = createdAt;
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

  public LocalDate getCheckDate() {
    return checkDate;
  }

  public void setCheckDate(LocalDate checkDate) {
    this.checkDate = checkDate;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
