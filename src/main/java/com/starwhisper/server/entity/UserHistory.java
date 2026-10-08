package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户占卜历史：塔罗抽牌 / 铜钱起卦各存一条
 */
@Entity
@Table(name = "user_history")
public class UserHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  @Column(length = 16)
  private String type;          // TAROT 塔罗 / BAGUA 易经

  @Column(length = 128)
  private String title;         // 摘要标题，如 "三牌阵 · 宝剑八(正)/圣杯三(逆)/星币九(正)"

  // 实测三张牌阵的完整 JSON 约 1.5K 字符，1024 放不下，放宽到 4096
  @Column(length = 4096)
  private String detail;        // 结果明细的 JSON 字符串

  private LocalDateTime createdAt;

  public UserHistory() {
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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
