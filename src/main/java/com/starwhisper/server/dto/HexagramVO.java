package com.starwhisper.server.dto;

import com.starwhisper.server.entity.BaguaHexagram;

/**
 * 卦视图对象：卦面信息原样带给前端
 */
public class HexagramVO {

  private Integer seqNumber;    // 卦序 1-64
  private String name;          // 卦名
  private String symbol;        // Unicode 卦符（数据留存用）
  private String upperTrigram;  // 上卦
  private String lowerTrigram;  // 下卦
  private String lines;         // 六爻，自下而上，1=阳 0=阴
  private String judgment;      // 卦辞原文
  private String meaning;       // 现代解读
  private String fortuneLevel;  // 吉凶档

  public static HexagramVO of(BaguaHexagram hexagram) {
    HexagramVO vo = new HexagramVO();
    vo.seqNumber = hexagram.getSeqNumber();
    vo.name = hexagram.getName();
    vo.symbol = hexagram.getSymbol();
    vo.upperTrigram = hexagram.getUpperTrigram();
    vo.lowerTrigram = hexagram.getLowerTrigram();
    vo.lines = hexagram.getLines();
    vo.judgment = hexagram.getJudgment();
    vo.meaning = hexagram.getMeaning();
    vo.fortuneLevel = hexagram.getFortuneLevel();
    return vo;
  }

  public Integer getSeqNumber() {
    return seqNumber;
  }

  public String getName() {
    return name;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getUpperTrigram() {
    return upperTrigram;
  }

  public String getLowerTrigram() {
    return lowerTrigram;
  }

  public String getLines() {
    return lines;
  }

  public String getJudgment() {
    return judgment;
  }

  public String getMeaning() {
    return meaning;
  }

  public String getFortuneLevel() {
    return fortuneLevel;
  }
}
