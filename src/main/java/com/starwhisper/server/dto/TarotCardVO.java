package com.starwhisper.server.dto;

import com.starwhisper.server.entity.TarotCard;

/**
 * 塔罗牌视图对象：把牌面信息原样带给前端
 */
public class TarotCardVO {

  private Long id;
  private String name;           // 中文牌名
  private String nameEn;         // 英文牌名
  private String arcanaGroup;    // MAJOR / WANDS / CUPS / SWORDS / PENTACLES
  private Integer cardNumber;    // 大阿尔卡纳 0-21，小阿尔卡纳 1-14
  private String uprightKeywords;
  private String uprightMeaning;
  private String reversedKeywords;
  private String reversedMeaning;

  public static TarotCardVO of(TarotCard card) {
    TarotCardVO vo = new TarotCardVO();
    vo.id = card.getId();
    vo.name = card.getName();
    vo.nameEn = card.getNameEn();
    vo.arcanaGroup = card.getArcanaGroup();
    vo.cardNumber = card.getCardNumber();
    vo.uprightKeywords = card.getUprightKeywords();
    vo.uprightMeaning = card.getUprightMeaning();
    vo.reversedKeywords = card.getReversedKeywords();
    vo.reversedMeaning = card.getReversedMeaning();
    return vo;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getNameEn() {
    return nameEn;
  }

  public String getArcanaGroup() {
    return arcanaGroup;
  }

  public Integer getCardNumber() {
    return cardNumber;
  }

  public String getUprightKeywords() {
    return uprightKeywords;
  }

  public String getUprightMeaning() {
    return uprightMeaning;
  }

  public String getReversedKeywords() {
    return reversedKeywords;
  }

  public String getReversedMeaning() {
    return reversedMeaning;
  }
}
