package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * 每日运势：一个星座一天一条
 * (sign_id, fortune_date) 加了唯一约束，同一天不会重复生成
 */
@Entity
@Table(name = "fortune_daily", uniqueConstraints = @UniqueConstraint(columnNames = {"sign_id", "fortune_date"}))
public class FortuneDaily {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long signId;          // 关联 signs.id（逻辑外键，不建 JPA 关联，保持简单）
  private LocalDate fortuneDate; // 运势日期

  // 五项评分，1~5 星
  private Integer overallScore; // 综合
  private Integer loveScore;    // 爱情
  private Integer careerScore;  // 事业
  private Integer wealthScore;  // 财运
  private Integer healthScore;  // 健康

  private String luckyColor;    // 幸运色
  private Integer luckyNumber;  // 幸运数字 1~99
  private String luckyTime;     // 吉时，如 "13:00-15:00"

  @Column(length = 512)
  private String summary;       // 一句话运势点评

  @Column(length = 255)
  private String doText;        // 宜：如 "表白、约会、早睡"

  @Column(length = 255)
  private String dontText;      // 忌：如 "熬夜、冲动消费"

  private Long pairSignId;      // 速配星座（同样是 signs.id）

  // ↓↓↓ 第 5 课：接入外部数据源（万维易源 ShowAPI）后新增的字段，都允许为空 ↓↓↓

  private String luckyDirection; // 吉利方位（ShowAPI 提供，本地生成没有，为 null）

  @Column(length = 512)
  private String dayNotice;     // 今日提醒（ShowAPI 提供）

  @Column(length = 512)
  private String loveTxt;       // 爱情运详解段落

  @Column(length = 512)
  private String workTxt;       // 事业运详解段落

  @Column(length = 512)
  private String moneyTxt;      // 财运详解段落

  @Column(length = 512)
  private String healthTxt;     // 健康运详解（暂时没有数据源提供，预留）

  @Column(length = 16)
  private String source = "LOCAL"; // 数据来源：LOCAL=本地生成，SHOWAPI=万维易源接口

  public FortuneDaily() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getSignId() {
    return signId;
  }

  public void setSignId(Long signId) {
    this.signId = signId;
  }

  public LocalDate getFortuneDate() {
    return fortuneDate;
  }

  public void setFortuneDate(LocalDate fortuneDate) {
    this.fortuneDate = fortuneDate;
  }

  public Integer getOverallScore() {
    return overallScore;
  }

  public void setOverallScore(Integer overallScore) {
    this.overallScore = overallScore;
  }

  public Integer getLoveScore() {
    return loveScore;
  }

  public void setLoveScore(Integer loveScore) {
    this.loveScore = loveScore;
  }

  public Integer getCareerScore() {
    return careerScore;
  }

  public void setCareerScore(Integer careerScore) {
    this.careerScore = careerScore;
  }

  public Integer getWealthScore() {
    return wealthScore;
  }

  public void setWealthScore(Integer wealthScore) {
    this.wealthScore = wealthScore;
  }

  public Integer getHealthScore() {
    return healthScore;
  }

  public void setHealthScore(Integer healthScore) {
    this.healthScore = healthScore;
  }

  public String getLuckyColor() {
    return luckyColor;
  }

  public void setLuckyColor(String luckyColor) {
    this.luckyColor = luckyColor;
  }

  public Integer getLuckyNumber() {
    return luckyNumber;
  }

  public void setLuckyNumber(Integer luckyNumber) {
    this.luckyNumber = luckyNumber;
  }

  public String getLuckyTime() {
    return luckyTime;
  }

  public void setLuckyTime(String luckyTime) {
    this.luckyTime = luckyTime;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getDoText() {
    return doText;
  }

  public void setDoText(String doText) {
    this.doText = doText;
  }

  public String getDontText() {
    return dontText;
  }

  public void setDontText(String dontText) {
    this.dontText = dontText;
  }

  public Long getPairSignId() {
    return pairSignId;
  }

  public void setPairSignId(Long pairSignId) {
    this.pairSignId = pairSignId;
  }

  public String getLuckyDirection() {
    return luckyDirection;
  }

  public void setLuckyDirection(String luckyDirection) {
    this.luckyDirection = luckyDirection;
  }

  public String getDayNotice() {
    return dayNotice;
  }

  public void setDayNotice(String dayNotice) {
    this.dayNotice = dayNotice;
  }

  public String getLoveTxt() {
    return loveTxt;
  }

  public void setLoveTxt(String loveTxt) {
    this.loveTxt = loveTxt;
  }

  public String getWorkTxt() {
    return workTxt;
  }

  public void setWorkTxt(String workTxt) {
    this.workTxt = workTxt;
  }

  public String getMoneyTxt() {
    return moneyTxt;
  }

  public void setMoneyTxt(String moneyTxt) {
    this.moneyTxt = moneyTxt;
  }

  public String getHealthTxt() {
    return healthTxt;
  }

  public void setHealthTxt(String healthTxt) {
    this.healthTxt = healthTxt;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
