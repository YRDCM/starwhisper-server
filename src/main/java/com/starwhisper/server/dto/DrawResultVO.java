package com.starwhisper.server.dto;

import java.util.List;

/**
 * 抽牌结果视图对象（JSON 契约，前端照此编码）：
 * {spread:"three", spreadName:"时间之流", cards:[...]}
 */
public class DrawResultVO {

  private String spread;            // 牌阵 key：single/three/choice/love/celtic/hexagram
  private String spreadName;        // 牌阵中文名
  private List<DrawnCardVO> cards;  // 抽到的牌（按抽牌顺序，牌位已解析进每张牌）

  public DrawResultVO(String spread, String spreadName, List<DrawnCardVO> cards) {
    this.spread = spread;
    this.spreadName = spreadName;
    this.cards = cards;
  }

  public String getSpread() {
    return spread;
  }

  public String getSpreadName() {
    return spreadName;
  }

  public List<DrawnCardVO> getCards() {
    return cards;
  }
}
