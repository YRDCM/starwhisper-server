package com.starwhisper.server.service;

import com.starwhisper.server.dto.FortuneVO;
import com.starwhisper.server.entity.FortuneDaily;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.FortuneDailyRepository;
import com.starwhisper.server.repository.SignRepository;
import com.starwhisper.server.service.impl.LocalFortuneProvider;
import com.starwhisper.server.service.impl.ShowapiFortuneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 每日运势业务层
 * 数据源策略：
 * - 今天及未来：优先 ShowAPI（如果启用），失败自动回退本地生成，绝不让外部接口挂掉拖垮我们
 * - 过去日期：ShowAPI 不提供历史数据，直接查库或本地生成
 * 核心思路不变：先查库，库里没有（或来源是本地且 ShowAPI 可用）就取数并落库
 */
@Service
public class FortuneService {

  private static final Logger log = LoggerFactory.getLogger(FortuneService.class);

  private final FortuneDailyRepository fortuneDailyRepository;
  private final SignRepository signRepository;
  private final LocalFortuneProvider localFortuneProvider;
  private final ShowapiFortuneProvider showapiFortuneProvider;

  public FortuneService(FortuneDailyRepository fortuneDailyRepository,
                        SignRepository signRepository,
                        LocalFortuneProvider localFortuneProvider,
                        ShowapiFortuneProvider showapiFortuneProvider) {
    this.fortuneDailyRepository = fortuneDailyRepository;
    this.signRepository = signRepository;
    this.localFortuneProvider = localFortuneProvider;
    this.showapiFortuneProvider = showapiFortuneProvider;
  }

  /**
   * 查某星座某一天的运势，没有就自动生成/抓取
   *
   * @param signQuery 星座标识：英文名（如 aries，忽略大小写）或中文名（如 天秤座）
   * @param date      运势日期
   */
  public FortuneVO getFortune(String signQuery, LocalDate date) {
    Sign sign = resolveSign(signQuery);
    FortuneDaily fortune = getOrFetch(sign, date);
    return toVO(fortune, sign);
  }

  /**
   * 查某星座最近 7 天运势（今天往前 6 天），缺失的日期自动生成
   * 返回按日期升序；今天的可能是 SHOWAPI，过去的是 LOCAL，混合来源属正常
   */
  public List<FortuneVO> getWeek(String signQuery) {
    Sign sign = resolveSign(signQuery);
    LocalDate today = LocalDate.now();

    List<FortuneVO> week = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate date = today.minusDays(i);
      week.add(toVO(getOrFetch(sign, date), sign));
    }
    return week;
  }

  /**
   * 为某一天生成全部 12 星座的运势（已存在的跳过，幂等）
   * 批量预生成固定走本地生成器，不消耗外部 API 额度
   *
   * @return 本次实际新增的条数
   */
  public int generateForDate(LocalDate date) {
    List<Sign> signs = signRepository.findAllByOrderByIdAsc();

    int created = 0;
    for (Sign sign : signs) {
      if (fortuneDailyRepository.findBySignIdAndFortuneDate(sign.getId(), date).isPresent()) {
        continue; // 已有数据，跳过
      }
      fortuneDailyRepository.save(localFortuneProvider.fetch(sign, date));
      created++;
    }
    return created;
  }

  /**
   * 取数主流程：
   * 1. 今天/未来且 ShowAPI 启用：行不存在 或 行是本地生成的 → 走 ShowAPI 抓取并落库（本地行会被升级覆盖）
   * 2. ShowAPI 任何失败 → 记 warn 日志，回退本地逻辑
   * 3. 过去日期 / ShowAPI 未启用 → 查库，没有再本地生成
   */
  private FortuneDaily getOrFetch(Sign sign, LocalDate date) {
    Optional<FortuneDaily> existing =
        fortuneDailyRepository.findBySignIdAndFortuneDate(sign.getId(), date);

    boolean isTodayOrFuture = !date.isBefore(LocalDate.now());
    boolean needShowapi = isTodayOrFuture && showapiFortuneProvider.isActive()
        && (existing.isEmpty() || !"SHOWAPI".equals(existing.get().getSource()));

    if (needShowapi) {
      try {
        FortuneDaily fetched = showapiFortuneProvider.fetch(sign, date);
        // upsert：如果已有本地行，保留 id 整体覆盖字段（含 source 升级为 SHOWAPI）
        existing.ifPresent(old -> fetched.setId(old.getId()));
        return fortuneDailyRepository.save(fetched);
      } catch (Exception e) {
        // 外部接口挂了/超时/返回错误码都不应该让用户看到 500，回退本地
        log.warn("ShowAPI 运势获取失败，回退本地生成：sign={}, date={}, 原因={}",
            sign.getNameEn(), date, e.getMessage());
      }
    }

    return existing.orElseGet(() -> fortuneDailyRepository.save(localFortuneProvider.fetch(sign, date)));
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
