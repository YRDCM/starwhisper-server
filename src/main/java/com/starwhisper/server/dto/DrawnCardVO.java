package com.starwhisper.server.dto;

import com.starwhisper.server.entity.TarotCard;

/**
 * 抽到的牌视图对象：牌面 + 正逆位 + 牌阵位置，
 * keywords / meaning 已经按抽到的朝向解析好了，前端直接展示
 */
public class DrawnCardVO {

  private TarotCardVO card;     // 牌面完整信息
  private String orientation;   // upright=正位 / reversed=逆位
  private String position;      // 牌阵位置：过去/现在/未来，单张时为 null
  private String keywords;      // 当前朝向的关键词
  private String meaning;       // 当前朝向的解读

  public static DrawnCardVO of(TarotCard card, boolean upright, String position) {
    DrawnCardVO vo = new DrawnCardVO();
    vo.card = TarotCardVO.of(card);
    vo.orientation = upright ? "upright" : "reversed";
    vo.position = position;
    // 按抽到的朝向挑选关键词和解读，前端不用再判断
    vo.keywords = upright ? card.getUprightKeywords() : card.getReversedKeywords();
    vo.meaning = upright ? card.getUprightMeaning() : card.getReversedMeaning();
    return vo;
  }

  public TarotCardVO getCard() {
    return card;
  }

  public String getOrientation() {
    return orientation;
  }

  public String getPosition() {
    return position;
  }

  public String getKeywords() {
    return keywords;
  }

  public String getMeaning() {
    return meaning;
  }
}
