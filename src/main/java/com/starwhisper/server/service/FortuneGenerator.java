package com.starwhisper.server.service;

import com.starwhisper.server.entity.FortuneDaily;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 运势生成器：纯本地的"伪随机算命先生"
 * 用 (signId, date) 做随机种子，同一个星座同一天永远算出同样的结果，
 * 所以重复请求不会变、重启服务也不会变。
 */
@Component
public class FortuneGenerator {

  // 点评池：按综合评分分三档，各备 5 条
  private static final String[] SUMMARY_HIGH = {
      "运势如虹的一天，做什么都顺手，大胆往前冲就对了。",
      "星光眷顾，贵人运在线，适合推进搁置已久的计划。",
      "状态满格，灵感不断，今天的你就是人群中最亮的星。",
      "好事接二连三，保持微笑，幸运会主动找上门。",
      "能量爆棚的一天，适合挑战高难度目标，胜算很大。"
  };
  private static final String[] SUMMARY_MID = {
      "平稳中带点小惊喜，按部就班也能收获不错的一天。",
      "运势中规中矩，守住节奏别急躁，好事正在路上。",
      "小有波动但总体向好，注意细节就能避开小坑。",
      "适合沉淀和积累的一天，别小看每一步的努力。",
      "机会藏在日常里，多留心身边人的一句话。"
  };
  private static final String[] SUMMARY_LOW = {
      "今天宜低调蓄力，别做重大决定，稳住就是赢。",
      "运势稍有低迷，早点休息，养足精神明天再战。",
      "容易遇到小波折，深呼吸，慢一点反而更快。",
      "情绪容易起伏的一天，独处充电比社交更划算。",
      "不宜冲动行事，把重要的事先放一放，等风来。"
  };

  // 宜/忌文案池：随机挑 3 条用顿号拼起来
  private static final String[] DO_POOL = {
      "表白", "约会", "早睡", "运动", "读书", "存钱", "社交", "旅行",
      "面试", "签约", "打扫", "冥想", "复盘", "学习", "下厨", "晒太阳", "断舍离", "喝热水"
  };
  private static final String[] DONT_POOL = {
      "熬夜", "赖床", "冲动消费", "冷战", "拖延", "暴饮暴食", "争吵", "借贷",
      "久坐", "贪杯", "八卦", "翻旧账", "冒险投资", "迟到", "情绪化", "临时抱佛脚", "跷二郎腿", "空腹喝咖啡"
  };

  // 幸运色池
  private static final String[] COLOR_POOL = {
      "樱花粉", "薄荷绿", "天空蓝", "奶白色", "焦糖棕", "葡萄紫",
      "柠檬黄", "朱砂红", "雾霾蓝", "燕麦色", "珊瑚橙", "墨绿色", "香槟金", "象牙白"
  };

  // 吉时池
  private static final String[] TIME_POOL = {
      "07:00-09:00", "09:00-11:00", "11:00-13:00", "13:00-15:00",
      "15:00-17:00", "17:00-19:00", "19:00-21:00", "21:00-23:00"
  };

  /**
   * 生成某星座某一天的运势（不落库，由调用方决定保存）
   *
   * @param signId     星座 id
   * @param date       运势日期
   * @param allSignIds 全部星座 id（按 id 升序），用于挑选速配星座
   */
  public FortuneDaily generate(Long signId, LocalDate date, List<Long> allSignIds) {
    // 固定种子：同一星座同一天结果永远一致
    // 为什么种子要再混洗：相邻日期算出的原始种子也相邻（只差 1），
    // 而 java.util.Random 是线性同余生成器，相邻种子的第一次 nextInt 输出
    // 高度相关（高位几乎不动），导致"第一个抽取的评分"连续多天卡在同一个值。
    // 所以先用 murmur3 风格的 64 位 finalizer 把种子打散（雪崩效应），
    // 让相邻日期的结果互不相关，同时保持同样的输入永远得到同样的种子（确定性不变）。
    long seed = signId * 1000003L + date.toEpochDay();
    seed ^= seed >>> 33;
    seed *= 0xff51afd7ed558ccdL;
    seed ^= seed >>> 33;
    seed *= 0xc4ceb9fe1a85ec53L;
    seed ^= seed >>> 33;
    Random random = new Random(seed);

    FortuneDaily fortune = new FortuneDaily();
    fortune.setSignId(signId);
    fortune.setFortuneDate(date);

    // 评分 2~5（轻微偏向中高分，天天一星用户体验太差）
    // 注意：抽取顺序固定，保证可复现
    fortune.setOverallScore(2 + random.nextInt(4));
    fortune.setLoveScore(2 + random.nextInt(4));
    fortune.setCareerScore(2 + random.nextInt(4));
    fortune.setWealthScore(2 + random.nextInt(4));
    fortune.setHealthScore(2 + random.nextInt(4));

    fortune.setLuckyNumber(1 + random.nextInt(99));
    fortune.setLuckyColor(pickOne(COLOR_POOL, random));
    fortune.setLuckyTime(pickOne(TIME_POOL, random));

    // 宜/忌各挑 3 条，洗牌后取前三个，保证不重复
    fortune.setDoText(pickThree(DO_POOL, random));
    fortune.setDontText(pickThree(DONT_POOL, random));

    // 点评：按综合评分选档，再抽一条
    int overall = fortune.getOverallScore();
    String[] pool = overall >= 4 ? SUMMARY_HIGH : (overall == 3 ? SUMMARY_MID : SUMMARY_LOW);
    fortune.setSummary(pickOne(pool, random));

    // 速配星座：从全部星座里挑一个不是自己
    fortune.setPairSignId(pickPairSign(signId, allSignIds, random));

    return fortune;
  }

  private String pickOne(String[] pool, Random random) {
    return pool[random.nextInt(pool.length)];
  }

  private String pickThree(String[] pool, Random random) {
    List<String> copy = new ArrayList<>(List.of(pool));
    Collections.shuffle(copy, random);
    return String.join("、", copy.subList(0, 3));
  }

  private Long pickPairSign(Long signId, List<Long> allSignIds, Random random) {
    int index = allSignIds.indexOf(signId);
    if (index < 0 || allSignIds.size() < 2) {
      return null; // 数据异常时给个兜底，不阻断主流程
    }
    // 往后挪 1~size-1 个位置，一定不是自己
    int offset = 1 + random.nextInt(allSignIds.size() - 1);
    return allSignIds.get((index + offset) % allSignIds.size());
  }
}
