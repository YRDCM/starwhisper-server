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
}
