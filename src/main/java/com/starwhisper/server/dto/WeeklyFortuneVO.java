package com.starwhisper.server.dto;

/**
 * 本周运势视图对象（ShowAPI 872-1 的 week 节点拍平）
 * 与每日运势不同：周运没有本地生成器兜底——失败直接报错，
 * 所以这个 VO 只会出现在真实取数（或显式 mock 模式）成功的场景
 */
public class WeeklyFortuneVO {

  private Long signId;
  private String signName;
  private String signNameEn;
  private String signEmoji;

  // 四维评分（5 分制；上游可能是数字或字符串，防御性解析，解析不出为 null）
  private Integer overallScore;
  private Integer loveScore;
  private Integer careerScore;
  private Integer wealthScore;

  // 四段文案（上游若缺字段则为 null，前端按可空处理，不显示该区块）
  private String generalTxt;
  private String loveTxt;
  private String workTxt;
  private String moneyTxt;

  private String weekNotice;    // 本周提醒
  private String luckyColor;
  private Integer luckyNumber;
  private String luckyDay;      // 幸运日，如"星期三"
  private String pairSignName;  // 贵人星座（中文名）
  private String nemesisSignName; // 小人星座（中文名）

  // 周区间，原样透传上游 time 字段（如 20260913-20260920），前端自己格式化
  private String timeRange;

  private String source;        // SHOWAPI（mock 模式亦为 SHOWAPI，与现有日运 mock 行为一致）

  public Long getSignId() {
    return signId;
  }

  public void setSignId(Long signId) {
    this.signId = signId;
  }

  public String getSignName() {
    return signName;
  }

  public void setSignName(String signName) {
    this.signName = signName;
  }

  public String getSignNameEn() {
    return signNameEn;
  }

  public void setSignNameEn(String signNameEn) {
    this.signNameEn = signNameEn;
  }

  public String getSignEmoji() {
    return signEmoji;
  }

  public void setSignEmoji(String signEmoji) {
    this.signEmoji = signEmoji;
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

  public String getGeneralTxt() {
    return generalTxt;
  }

  public void setGeneralTxt(String generalTxt) {
    this.generalTxt = generalTxt;
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

  public String getWeekNotice() {
    return weekNotice;
  }

  public void setWeekNotice(String weekNotice) {
    this.weekNotice = weekNotice;
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

  public String getLuckyDay() {
    return luckyDay;
  }

  public void setLuckyDay(String luckyDay) {
    this.luckyDay = luckyDay;
  }

  public String getPairSignName() {
    return pairSignName;
  }

  public void setPairSignName(String pairSignName) {
    this.pairSignName = pairSignName;
  }

  public String getNemesisSignName() {
    return nemesisSignName;
  }

  public void setNemesisSignName(String nemesisSignName) {
    this.nemesisSignName = nemesisSignName;
  }

  public String getTimeRange() {
    return timeRange;
  }

  public void setTimeRange(String timeRange) {
    this.timeRange = timeRange;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
