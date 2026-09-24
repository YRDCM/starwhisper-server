package com.starwhisper.server.service;

import com.starwhisper.server.dto.DrawnCardVO;
import com.starwhisper.server.dto.TarotCardVO;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.entity.TarotCard;
import com.starwhisper.server.repository.SignRepository;
import com.starwhisper.server.repository.TarotCardRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 塔罗业务层
 * 抽牌用 SecureRandom（真随机，每次结果都不同）；
 * 每日一牌用种子混洗（伪随机，同一天同一星座结果固定），两种场景两种随机策略
 */
@Service
public class TarotService {

  // 牌组展示顺序：大阿尔卡纳在前，小阿尔卡纳按 权杖→圣杯→宝剑→星币
  private static final Map<String, Integer> GROUP_ORDER = Map.of(
      "MAJOR", 0, "WANDS", 1, "CUPS", 2, "SWORDS", 3, "PENTACLES", 4);

  // 三张牌阵的位置名
  private static final String[] POSITIONS_THREE = {"过去", "现在", "未来"};

  // 每日一牌的盐值：和运势模块的随机流区分开（运势主流程是 0，宜忌是 1/2）
  private static final long TAROT_DAILY_SALT = 100;

  private final TarotCardRepository tarotCardRepository;
  private final SignRepository signRepository;
  private final FortuneGenerator fortuneGenerator;

  public TarotService(TarotCardRepository tarotCardRepository,
                      SignRepository signRepository,
                      FortuneGenerator fortuneGenerator) {
    this.tarotCardRepository = tarotCardRepository;
    this.signRepository = signRepository;
    this.fortuneGenerator = fortuneGenerator;
  }

  /**
   * 全部 78 张牌：大阿尔卡纳 0-21 在前，然后四个花色各 1-14
   */
  public List<TarotCardVO> listCards() {
    return orderedDeck().stream().map(TarotCardVO::of).toList();
  }

  /**
   * 随机抽牌：count 只能是 1（单张）或 3（过去/现在/未来牌阵）
   * 一次抽牌内不会有重复的牌，每张牌独立掷 50/50 决定正逆位
   */
  public List<DrawnCardVO> draw(int count) {
    if (count != 1 && count != 3) {
      throw new IllegalArgumentException("抽牌数量只支持 1 张或 3 张，收到：" + count);
    }

    List<TarotCard> deck = new ArrayList<>(orderedDeck());
    // SecureRandom：抽牌是"占卜"场景，要的是不可预测的真随机
    SecureRandom secureRandom = new SecureRandom();
    java.util.Collections.shuffle(deck, secureRandom);

    List<DrawnCardVO> drawn = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      boolean upright = secureRandom.nextBoolean();
      String position = (count == 3) ? POSITIONS_THREE[i] : null;
      drawn.add(DrawnCardVO.of(deck.get(i), upright, position));
    }
    return drawn;
  }

  /**
   * 每日一牌：同一星座同一天永远抽到同一张牌同一个朝向（确定性），
   * 不传星座则是"今日通用牌"。盐值 100 与运势模块的随机流隔离
   */
  public DrawnCardVO daily(String signQuery) {
    Long signId = resolveSignIdOrZero(signQuery);
    LocalDate today = LocalDate.now();

    List<TarotCard> deck = orderedDeck();
    Random random = fortuneGenerator.newSeededRandom(signId, today, TAROT_DAILY_SALT);
    int index = random.nextInt(deck.size());
    boolean upright = random.nextBoolean();

    return DrawnCardVO.of(deck.get(index), upright, null);
  }

  /**
   * 按固定顺序取整副牌（排序而非依赖数据库 id，保证结果稳定）
   */
  private List<TarotCard> orderedDeck() {
    List<TarotCard> cards = new ArrayList<>(tarotCardRepository.findAll());
    cards.sort(Comparator
        .comparingInt((TarotCard c) -> GROUP_ORDER.getOrDefault(c.getArcanaGroup(), 99))
        .thenComparingInt(TarotCard::getCardNumber));
    return cards;
  }

  /**
   * 解析星座参数：传了就必须认识（英文名忽略大小写或中文名），没传返回 0 表示"通用"
   */
  private Long resolveSignIdOrZero(String signQuery) {
    if (signQuery == null || signQuery.isBlank()) {
      return 0L; // 不传星座 = 今日通用牌
    }
    String query = signQuery.trim();
    return signRepository.findByNameEnIgnoreCase(query)
        .or(() -> signRepository.findByName(query))
        .map(Sign::getId)
        .orElseThrow(() -> new IllegalArgumentException("不认识的星座：" + query + "（支持英文名或中文名，如 aries / 白羊座）"));
  }
}
