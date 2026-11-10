package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户反馈。
 *
 * 游客也能提交（user_id / openid 为 null），这是有意为之：
 * 小程序此前的所有通道都断了，一个连反馈都发不了的产品等于聋子。
 */
@Entity
@Table(name = "feedback")
public class Feedback {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  @Column(length = 64)
  private String openid;

  @Column(length = 64)
  private String ip;

  @Column(length = 64)
  private String page;

  @Column(length = 1000, nullable = false)
  private String content;

  @Column(length = 128)
  private String contact;

  private LocalDateTime createdAt;

  public Feedback() {
  }

  public Feedback(Long userId, String openid, String ip, String page, String content, String contact,
                  LocalDateTime createdAt) {
    this.userId = userId;
    this.openid = openid;
    this.ip = ip;
    this.page = page;
    this.content = content;
    this.contact = contact;
    this.createdAt = createdAt;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getOpenid() { return openid; }
  public void setOpenid(String openid) { this.openid = openid; }
  public String getIp() { return ip; }
  public void setIp(String ip) { this.ip = ip; }
  public String getPage() { return page; }
  public void setPage(String page) { this.page = page; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getContact() { return contact; }
  public void setContact(String contact) { this.contact = contact; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
