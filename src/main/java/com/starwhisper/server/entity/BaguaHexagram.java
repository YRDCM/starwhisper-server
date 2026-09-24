package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 易经六十四卦之一卦
 */
@Entity
@Table(name = "bagua_hexagram")
public class BaguaHexagram {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Integer seqNumber;    // 卦序 1-64（文王卦序）

  private String name;          // 卦名：乾为天 / 水雷屯

  private String symbol;        // Unicode 卦符（䷀-䷿），仅作数据留存，前端不拿它渲染

  private String upperTrigram;  // 上卦名：乾兑离震巽坎艮坤
  private String lowerTrigram;  // 下卦名

  // 注意：LINES 是 MySQL 8.0 保留字，列名必须改叫 hexagram_lines，Java 字段和 JSON 仍叫 lines
  @Column(name = "hexagram_lines", length = 6)
  private String lines;         // 六爻，自下而上，阳='1' 阴='0'（乾="111111"，泰="111000"）

  @Column(length = 512)
  private String judgment;      // 卦辞（《易经》原文，公有领域）

  @Column(length = 512)
  private String meaning;       // 现代解读一句

  private String fortuneLevel;  // 吉凶档：上上/上吉/中吉/中平/中下/下下

  public BaguaHexagram() {
  }

  public BaguaHexagram(Integer seqNumber, String name, String symbol,
                       String upperTrigram, String lowerTrigram, String lines,
                       String judgment, String meaning, String fortuneLevel) {
    this.seqNumber = seqNumber;
    this.name = name;
    this.symbol = symbol;
    this.upperTrigram = upperTrigram;
    this.lowerTrigram = lowerTrigram;
    this.lines = lines;
    this.judgment = judgment;
    this.meaning = meaning;
    this.fortuneLevel = fortuneLevel;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Integer getSeqNumber() {
    return seqNumber;
  }

  public void setSeqNumber(Integer seqNumber) {
    this.seqNumber = seqNumber;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getUpperTrigram() {
    return upperTrigram;
  }

  public void setUpperTrigram(String upperTrigram) {
    this.upperTrigram = upperTrigram;
  }

  public String getLowerTrigram() {
    return lowerTrigram;
  }

  public void setLowerTrigram(String lowerTrigram) {
    this.lowerTrigram = lowerTrigram;
  }

  public String getLines() {
    return lines;
  }

  public void setLines(String lines) {
    this.lines = lines;
  }

  public String getJudgment() {
    return judgment;
  }

  public void setJudgment(String judgment) {
    this.judgment = judgment;
  }

  public String getMeaning() {
    return meaning;
  }

  public void setMeaning(String meaning) {
    this.meaning = meaning;
  }

  public String getFortuneLevel() {
    return fortuneLevel;
  }

  public void setFortuneLevel(String fortuneLevel) {
    this.fortuneLevel = fortuneLevel;
  }
}
