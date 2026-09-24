package com.starwhisper.server.service;

import com.starwhisper.server.dto.FortuneVO;
import com.starwhisper.server.entity.FortuneDaily;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.FortuneDailyRepository;
import com.starwhisper.server.repository.SignRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日运势业务层
 * 核心思路：先查库，库里没有就现场生成并落库（惰性生成），下次直接读库
 */
@Service
public class FortuneService {

  private final FortuneDailyRepository fortuneDailyRepository;
  private final SignRepository signRepository;
  private final FortuneGenerator fortuneGenerator;

  public FortuneService(FortuneDailyRepository fortuneDailyRepository,
                        SignRepository signRepository,
                        FortuneGenerator fortuneGenerator) {
    this.fortuneDailyRepository = fortuneDailyRepository;
    this.signRepository = signRepository;
    this.fortuneGenerator = fortuneGenerator;
  }

  /**
   * 查某星座某一天的运势，没有就自动生成
   *
   * @param signQuery 星座标识：英文名（如 aries，忽略大小写）或中文名（如 天秤座）
   * @param date      运势日期
   */
  public FortuneVO getFortune(String signQuery, LocalDate date) {
    Sign sign = resolveSign(signQuery);
    FortuneDaily fortune = getOrCreate(sign, date);
    return toVO(fortune, sign);
  }

  /**
   * 查某星座最近 7 天运势（今天往前 6 天），缺失的日期自动生成
   * 返回按日期升序
   */
  public List<FortuneVO> getWeek(String signQuery) {
    Sign sign = resolveSign(signQuery);
    LocalDate today = LocalDate.now();

    List<FortuneVO> week = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate date = today.minusDays(i);
      week.add(toVO(getOrCreate(sign, date), sign));
    }
    return week;
  }

  /**
   * 为某一天生成全部 12 星座的运势（已存在的跳过，幂等）
   *
   * @return 本次实际新增的条数
   */
  public int generateForDate(LocalDate date) {
    List<Sign> signs = signRepository.findAllByOrderByIdAsc();
    List<Long> signIds = signs.stream().map(Sign::getId).toList();

    int created = 0;
    for (Sign sign : signs) {
      if (fortuneDailyRepository.findBySignIdAndFortuneDate(sign.getId(), date).isPresent()) {
        continue; // 已有数据，跳过
      }
      fortuneDailyRepository.save(fortuneGenerator.generate(sign.getId(), date, signIds));
      created++;
    }
    return created;
  }

  /**
   * 解析星座参数：先试英文名（忽略大小写），再试中文名，都不认识就报错
   * 异常交给全局异常处理器统一返回
   */
  private Sign resolveSign(String signQuery) {
    if (signQuery == null || signQuery.isBlank()) {
      throw new IllegalArgumentException("缺少星座参数 sign");
    }
    String query = signQuery.trim();
    return signRepository.findByNameEnIgnoreCase(query)
        .or(() -> signRepository.findByName(query))
        .orElseThrow(() -> new IllegalArgumentException("不认识的星座：" + query + "（支持英文名或中文名，如 aries / 白羊座）"));
  }

  /**
   * 惰性生成：查库 → 没有则生成并保存
   */
  private FortuneDaily getOrCreate(Sign sign, LocalDate date) {
    return fortuneDailyRepository.findBySignIdAndFortuneDate(sign.getId(), date)
        .orElseGet(() -> {
          List<Long> signIds = signRepository.findAllByOrderByIdAsc()
              .stream().map(Sign::getId).toList();
          return fortuneDailyRepository.save(fortuneGenerator.generate(sign.getId(), date, signIds));
        });
  }

  /**
   * 组装 VO：补上星座信息和速配星座信息
   */
  private FortuneVO toVO(FortuneDaily fortune, Sign sign) {
    Sign pairSign = null;
    if (fortune.getPairSignId() != null) {
      pairSign = signRepository.findById(fortune.getPairSignId()).orElse(null);
    }
    return FortuneVO.of(fortune, sign, pairSign);
  }
}
