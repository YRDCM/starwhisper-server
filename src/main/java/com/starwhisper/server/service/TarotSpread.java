package com.starwhisper.server.service;

/**
 * 塔罗牌阵定义（硬编码，无需建表）
 * positions 数组的顺序就是抽牌顺序，第 i 张牌放到 positions[i]
 */
public enum TarotSpread {

  SINGLE("single", "单牌指引", new String[]{"指引"}),
  THREE("three", "时间之流", new String[]{"过去", "现在", "未来"}),
  CHOICE("choice", "二选一", new String[]{"现状", "选择 A", "选择 B"}),
  LOVE("love", "爱情十字", new String[]{"你的状态", "对方的状态", "关系现状", "现实阻碍", "结果与建议"});

  private final String key;        // 接口参数值
  private final String name;       // 中文牌阵名
  private final String[] positions; // 牌位名（按抽牌顺序）

  TarotSpread(String key, String name, String[] positions) {
    this.key = key;
    this.name = name;
    this.positions = positions;
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
    throw new IllegalArgumentException("不支持的牌阵：" + key + "（支持 single/three/choice/love）");
  }
}
