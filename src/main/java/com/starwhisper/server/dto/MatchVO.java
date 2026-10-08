package com.starwhisper.server.dto;

import com.starwhisper.server.entity.Sign;

/**
 * 星座配对结果视图对象（JSON 契约，前端照此编码，字段不得改名）
 */
public class MatchVO {

  private SignBrief star1;      // 甲方星座
  private SignBrief star2;      // 乙方星座
  private Scores scores;        // 各项指数（0-100）
  private String proportion;    // 比重，如 "54:46"
  private String suggest;       // 恋爱建议
  private String predestination; // 缘分解析
  private String matchCase;     // 配对示例
  private String attention;     // 注意事项
  private String review;        // 点评
  private String source;        // SHOWAPI=真实接口 / LOCAL=本地兜底

  /**
   * 星座简要信息
   */
  public static class SignBrief {
    private Long signId;
    private String name;
    private String nameEn;
    private String emoji;

    public static SignBrief of(Sign sign) {
      SignBrief brief = new SignBrief();
      brief.signId = sign.getId();
      brief.name = sign.getName();
      brief.nameEn = sign.getNameEn();
      brief.emoji = sign.getEmoji();
      return brief;
    }

    public Long getSignId() {
      return signId;
    }

    public String getName() {
      return name;
    }

    public String getNameEn() {
      return nameEn;
    }

    public String getEmoji() {
      return emoji;
    }
  }

  /**
   * 配对指数（0-100）
   */
  public static class Scores {
    private Integer overall;     // 综合指数
    private Integer love;        // 爱情
    private Integer friendship;  // 友情
    private Integer marriage;    // 婚姻
    private Integer forever;     // 天长地久
    private Integer lqxy;        // 两情相悦
    private Integer affection;   // 亲情

    public Integer getOverall() {
      return overall;
    }

    public void setOverall(Integer overall) {
      this.overall = overall;
    }

    public Integer getLove() {
      return love;
    }

    public void setLove(Integer love) {
      this.love = love;
    }

    public Integer getFriendship() {
      return friendship;
    }

    public void setFriendship(Integer friendship) {
      this.friendship = friendship;
    }

    public Integer getMarriage() {
      return marriage;
    }

    public void setMarriage(Integer marriage) {
      this.marriage = marriage;
    }

    public Integer getForever() {
      return forever;
    }

    public void setForever(Integer forever) {
      this.forever = forever;
    }

    public Integer getLqxy() {
      return lqxy;
    }

    public void setLqxy(Integer lqxy) {
      this.lqxy = lqxy;
    }

    public Integer getAffection() {
      return affection;
    }

    public void setAffection(Integer affection) {
      this.affection = affection;
    }
  }

  public SignBrief getStar1() {
    return star1;
  }

  public void setStar1(SignBrief star1) {
    this.star1 = star1;
  }

  public SignBrief getStar2() {
    return star2;
  }

  public void setStar2(SignBrief star2) {
    this.star2 = star2;
  }

  public Scores getScores() {
    return scores;
  }

  public void setScores(Scores scores) {
    this.scores = scores;
  }

  public String getProportion() {
    return proportion;
  }

  public void setProportion(String proportion) {
    this.proportion = proportion;
  }

  public String getSuggest() {
    return suggest;
  }

  public void setSuggest(String suggest) {
    this.suggest = suggest;
  }

  public String getPredestination() {
    return predestination;
  }

  public void setPredestination(String predestination) {
    this.predestination = predestination;
  }

  public String getMatchCase() {
    return matchCase;
  }

  public void setMatchCase(String matchCase) {
    this.matchCase = matchCase;
  }

  public String getAttention() {
    return attention;
  }

  public void setAttention(String attention) {
    this.attention = attention;
  }

  public String getReview() {
    return review;
  }

  public void setReview(String review) {
    this.review = review;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
