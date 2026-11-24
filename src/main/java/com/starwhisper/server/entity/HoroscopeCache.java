package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * ShowAPI 原始响应缓存（872-1 运势 / 872-2 配对）
 * 免费额度 100 次/天，同一 key 当天只调一次外部接口：
 * 命中且 created_at 是当天就直接用，否则重新调外部接口并覆盖写
 */
@Entity
@Table(name = "horoscope_cache")
public class HoroscopeCache {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 160, unique = true, nullable = false)
  private String cacheKey;    // 如 aries:today:2026-10-09 / match:aries:taurus:1:0:2026-10-09

  @Column(columnDefinition = "TEXT")
  private String content;     // ShowAPI 原始数据 JSON（day 对象 / 配对 body 对象）

  private LocalDateTime createdAt; // 写入时间，判断是否"当天"用

  public HoroscopeCache() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCacheKey() {
    return cacheKey;
  }

  public void setCacheKey(String cacheKey) {
    this.cacheKey = cacheKey;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
