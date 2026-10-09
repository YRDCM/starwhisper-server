package com.starwhisper.server.service;

/**
 * 塔罗牌阵定义（硬编码，无需建表）
 * positions 数组的顺序就是抽牌顺序，第 i 张牌放到 positions[i]；
 * positionDescs[i] 是对应牌位的一句释义，前端解读面板直接展示
 */
public enum TarotSpread {

  SINGLE("single", "单牌指引",
      new String[]{"指引"},
      new String[]{"这张牌是你当下最需要的指引，直指问题核心"}),

  THREE("three", "时间之流",
      new String[]{"过去", "现在", "未来"},
      new String[]{"过去的经历与影响，是造就今天局面的源头",
          "你此刻所处的状态与心境",
          "照目前的轨迹发展下去，最可能出现的走向"}),

  CHOICE("choice", "二选一",
      new String[]{"现状", "选择 A", "选择 B"},
      new String[]{"你在这个抉择点上的真实处境",
          "选择 A 这条路会带来的发展与结果",
          "选择 B 这条路会带来的发展与结果"}),

  LOVE("love", "爱情十字",
      new String[]{"你的状态", "对方的状态", "关系现状", "现实阻碍", "结果与建议"},
      new String[]{"你在这段关系中的心态与状态",
          "对方在这段关系中的心态与状态",
          "这段关系此刻的真实面貌",
          "横在两人之间的现实阻碍或课题",
          "这段关系的可能走向，以及给你的建议"}),

  CELTIC("celtic", "凯尔特十字",
      new String[]{"现状核心", "障碍与挑战", "潜意识根源", "过去的印记", "显意识目标",
          "未来的发展", "自我认知", "环境与外力", "希望与恐惧", "最终结果"},
      new String[]{"这张牌代表你当前的处境核心，整件事的主题",
          "横在你面前的主要障碍或挑战，需要正面应对的力量",
          "藏在表象之下的潜意识动机与深层根源",
          "正在淡去的过去，对当前局面仍有影响的事件",
          "你意识层面追求的目标，或局面可能达到的最佳状态",
          "近期事态的发展方向，正在走近的能量",
          "你如何看待自己，你在这件事中的立场与态度",
          "周围环境与他人对这件事的影响",
          "你内心深处的希望与恐惧，它们同时在推动你",
          "综合所有牌位，这件事最可能的最终结果"}),

  HEXAGRAM("hexagram", "六芒星",
      new String[]{"过去", "现在", "未来", "阻碍", "助力", "建议", "结果"},
      new String[]{"过去发生的事，构成了问题的根源",
          "问题此刻的状态与你身处的局面",
          "事态自然发展下去的方向",
          "阻碍你前进的因素，需要留意的暗礁",
          "可以帮助你的人、资源或自身优势",
          "牌面给你的行动建议",
          "遵循建议之后，最可能迎来的结果"});

  private final String key;           // 接口参数值
  private final String name;          // 中文牌阵名
  private final String[] positions;    // 牌位名（按抽牌顺序）
  private final String[] positionDescs; // 牌位释义（与 positions 一一对应）

  TarotSpread(String key, String name, String[] positions, String[] positionDescs) {
    if (positions.length != positionDescs.length) {
      throw new IllegalStateException("牌位名与牌位释义数量不一致：" + key);
    }
    this.key = key;
    this.name = name;
    this.positions = positions;
    this.positionDescs = positionDescs;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String[] getPositions() {
    return positions;
  }

  public String[] getPositionDescs() {
    return positionDescs;
  }

  public int getCount() {
    return positions.length;
  }

  /**
   * 按 key 找牌阵，不认识就报错（消息直接透给前端）
   */
  public static TarotSpread fromKey(String key) {
    for (TarotSpread spread : values()) {
      if (spread.key.equalsIgnoreCase(key.trim())) {
        return spread;
      }
    }
    throw new IllegalArgumentException("不支持的牌阵：" + key + "（支持 single/three/choice/love/celtic/hexagram）");
  }
}
