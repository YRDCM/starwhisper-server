package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 塔罗牌（韦特体系 78 张：22 大阿尔卡纳 + 56 小阿尔卡纳）
 */
@Entity
@Table(name = "tarot_card")
public class TarotCard {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;         // 中文牌名：愚人 / 圣杯三
  private String nameEn;       // 英文牌名：The Fool / Three of Cups

  private String arcanaGroup;  // 牌组：MAJOR / WANDS / CUPS / SWORDS / PENTACLES

  private Integer cardNumber;  // 大阿尔卡纳 0-21；小阿尔卡纳 1-14（1=Ace，11=侍从 12=骑士 13=王后 14=国王）

  private String uprightKeywords; // 正位关键词，顿号分隔

  @Column(length = 512)
  private String uprightMeaning;  // 正位一句解读

  private String reversedKeywords; // 逆位关键词

  @Column(length = 512)
  private String reversedMeaning;  // 逆位一句解读

  public TarotCard() {
  }

  public TarotCard(String name, String nameEn, String arcanaGroup, Integer cardNumber,
                   String uprightKeywords, String uprightMeaning,
                   String reversedKeywords, String reversedMeaning) {
    this.name = name;
    this.nameEn = nameEn;
    this.arcanaGroup = arcanaGroup;
    this.cardNumber = cardNumber;
    this.uprightKeywords = uprightKeywords;
    this.uprightMeaning = uprightMeaning;
    this.reversedKeywords = reversedKeywords;
    this.reversedMeaning = reversedMeaning;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNameEn() {
    return nameEn;
  }

  public void setNameEn(String nameEn) {
    this.nameEn = nameEn;
  }

  public String getArcanaGroup() {
    return arcanaGroup;
  }

  public void setArcanaGroup(String arcanaGroup) {
    this.arcanaGroup = arcanaGroup;
  }

  public Integer getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(Integer cardNumber) {
    this.cardNumber = cardNumber;
  }

  public String getUprightKeywords() {
    return uprightKeywords;
  }

  public void setUprightKeywords(String uprightKeywords) {
    this.uprightKeywords = uprightKeywords;
  }

  public String getUprightMeaning() {
    return uprightMeaning;
  }

  public void setUprightMeaning(String uprightMeaning) {
    this.uprightMeaning = uprightMeaning;
  }

  public String getReversedKeywords() {
    return reversedKeywords;
  }

  public void setReversedKeywords(String reversedKeywords) {
    this.reversedKeywords = reversedKeywords;
  }

  public String getReversedMeaning() {
    return reversedMeaning;
  }

  public void setReversedMeaning(String reversedMeaning) {
    this.reversedMeaning = reversedMeaning;
  }
}
