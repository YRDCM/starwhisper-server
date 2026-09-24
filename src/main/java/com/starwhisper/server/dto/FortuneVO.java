package com.starwhisper.server.dto;

import com.starwhisper.server.entity.FortuneDaily;
import com.starwhisper.server.entity.Sign;

import java.time.LocalDate;

/**
 * 每日运势返回给前端的视图对象：
 * 把 FortuneDaily 和星座信息拍平成一个对象，前端不用再自己关联
 */
public class FortuneVO {

  private Long signId;
  private String signName;
  private String signNameEn;
  private String signEmoji;

  private LocalDate fortuneDate;

  private Integer overallScore;
  private Integer loveScore;
  private Integer careerScore;
  private Integer wealthScore;
  private Integer healthScore;

  private String luckyColor;
  private Integer luckyNumber;
  private String luckyTime;

  private String summary;
  private String doText;
  private String dontText;

  private String pairSignName;
  private String pairSignEmoji;

  // ↓↓↓ 外部数据源新增字段（本地生成的数据这些为 null，前端按可空处理） ↓↓↓
  private String luckyDirection; // 吉利方位
  private String dayNotice;      // 今日提醒
  private String loveTxt;        // 爱情运详解
  private String workTxt;        // 事业运详解
  private String moneyTxt;       // 财运详解
  private String healthTxt;      // 健康运详解（预留，暂无数据源）
  private String source;         // 数据来源：LOCAL / SHOWAPI

  /**
   * 由实体 + 星座信息组装 VO
   *
   * @param fortune   运势实体
   * @param sign      本星座
   * @param pairSign  速配星座（可能为 null）
   */
  public static FortuneVO of(FortuneDaily fortune, Sign sign, Sign pairSign) {
    FortuneVO vo = new FortuneVO();
    vo.signId = sign.getId();
    vo.signName = sign.getName();
    vo.signNameEn = sign.getNameEn();
    vo.signEmoji = sign.getEmoji();

    vo.fortuneDate = fortune.getFortuneDate();
    vo.overallScore = fortune.getOverallScore();
    vo.loveScore = fortune.getLoveScore();
    vo.careerScore = fortune.getCareerScore();
    vo.wealthScore = fortune.getWealthScore();
    vo.healthScore = fortune.getHealthScore();
    vo.luckyColor = fortune.getLuckyColor();
    vo.luckyNumber = fortune.getLuckyNumber();
    vo.luckyTime = fortune.getLuckyTime();
    vo.summary = fortune.getSummary();
    vo.doText = fortune.getDoText();
    vo.dontText = fortune.getDontText();

    if (pairSign != null) {
      vo.pairSignName = pairSign.getName();
      vo.pairSignEmoji = pairSign.getEmoji();
    }

    vo.luckyDirection = fortune.getLuckyDirection();
    vo.dayNotice = fortune.getDayNotice();
    vo.loveTxt = fortune.getLoveTxt();
    vo.workTxt = fortune.getWorkTxt();
    vo.moneyTxt = fortune.getMoneyTxt();
    vo.healthTxt = fortune.getHealthTxt();
    // 老数据 source 列是 NULL，对外统一兜底成 LOCAL
    vo.source = fortune.getSource() == null ? "LOCAL" : fortune.getSource();
    return vo;
  }

  public Long getSignId() {
    return signId;
  }

  public String getSignName() {
    return signName;
  }

  public String getSignNameEn() {
    return signNameEn;
  }

  public String getSignEmoji() {
    return signEmoji;
  }

  public LocalDate getFortuneDate() {
    return fortuneDate;
  }

  public Integer getOverallScore() {
    return overallScore;
  }

  public Integer getLoveScore() {
    return loveScore;
  }

  public Integer getCareerScore() {
    return careerScore;
  }

  public Integer getWealthScore() {
    return wealthScore;
  }

  public Integer getHealthScore() {
    return healthScore;
  }

  public String getLuckyColor() {
    return luckyColor;
  }

  public Integer getLuckyNumber() {
    return luckyNumber;
  }

  public String getLuckyTime() {
    return luckyTime;
  }

  public String getSummary() {
    return summary;
  }

  public String getDoText() {
    return doText;
  }

  public String getDontText() {
    return dontText;
  }

  public String getPairSignName() {
    return pairSignName;
  }

  public String getPairSignEmoji() {
    return pairSignEmoji;
  }

  public String getLuckyDirection() {
    return luckyDirection;
  }

  public String getDayNotice() {
    return dayNotice;
  }

  public String getLoveTxt() {
    return loveTxt;
  }

  public String getWorkTxt() {
    return workTxt;
  }

  public String getMoneyTxt() {
    return moneyTxt;
  }

  public String getHealthTxt() {
    return healthTxt;
  }

  public String getSource() {
    return source;
  }
}
