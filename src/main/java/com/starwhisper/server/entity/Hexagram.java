package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 六十四卦（每日一卦用，v4）
 * 与铜钱起卦的 bagua_hexagram 表相互独立：这张表面向"每日运势式"的轻量解读，
 * 带卦象 unicode 字符、现代解读和今日建议
 */
@Entity
@Table(name = "hexagram")
public class Hexagram {

  @Id
  private Integer id;         // 卦序 1-64（文王卦序），不用自增

  @Column(length = 16, nullable = false)
  private String name;        // 卦名，如 乾为天

  @Column(length = 8, nullable = false)
  private String symbol;      // 卦象 unicode 字符（U+4DC0 起，按卦序），如 ䷀

  @Column(length = 255, nullable = false)
  private String guaCi;       // 卦辞（《易经》原文）

  @Column(length = 1024, nullable = false)
  private String interpretation; // 现代解读（100-200 字）

  @Column(length = 512, nullable = false)
  private String advice;      // 今日建议（50-100 字）

  private Integer luckLevel;  // 运势等级 1-5，5 最吉

  public Hexagram() {
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
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

  public String getGuaCi() {
    return guaCi;
  }

  public void setGuaCi(String guaCi) {
    this.guaCi = guaCi;
  }

  public String getInterpretation() {
    return interpretation;
  }

  public void setInterpretation(String interpretation) {
    this.interpretation = interpretation;
  }

  public String getAdvice() {
    return advice;
  }

  public void setAdvice(String advice) {
    this.advice = advice;
  }

  public Integer getLuckLevel() {
    return luckLevel;
  }

  public void setLuckLevel(Integer luckLevel) {
    this.luckLevel = luckLevel;
  }
}
