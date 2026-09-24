package com.starwhisper.server.service;

import com.starwhisper.server.dto.CastVO;
import com.starwhisper.server.dto.HexagramVO;
import com.starwhisper.server.entity.BaguaHexagram;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.BaguaHexagramRepository;
import com.starwhisper.server.repository.SignRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 易经起卦业务层
 * 起卦用 SecureRandom（真随机）；每日一卦用种子混洗（确定性），盐值 200 与其他模块隔离
 */
@Service
public class BaguaService {

  // 每日一卦的盐值（运势=0/1/2，塔罗=100，易经=200，各模块随机流互不干扰）
  private static final long BAGUA_DAILY_SALT = 200;

  private final BaguaHexagramRepository baguaHexagramRepository;
  private final SignRepository signRepository;
  private final FortuneGenerator fortuneGenerator;

  public BaguaService(BaguaHexagramRepository baguaHexagramRepository,
                      SignRepository signRepository,
                      FortuneGenerator fortuneGenerator) {
    this.baguaHexagramRepository = baguaHexagramRepository;
    this.signRepository = signRepository;
    this.fortuneGenerator = fortuneGenerator;
  }

  /**
   * 全部六十四卦，按卦序 1-64 升序
   */
  public List<HexagramVO> listHexagrams() {
    return baguaHexagramRepository.findAll().stream()
        .sorted(Comparator.comparingInt(BaguaHexagram::getSeqNumber))
        .map(HexagramVO::of)
        .toList();
  }

  /**
   * 三枚铜钱法起卦：
   * 每爻掷 3 枚铜钱（字面=3，背面=2），合计：
   * 6=老阴（阴爻，变爻） 7=少阳（阳爻） 8=少阴（阴爻） 9=老阳（阳爻，变爻）
   * 自下而上起六爻得本卦；有变爻时，变爻阴阳翻转得之卦
   */
  public CastVO cast() {
    SecureRandom random = new SecureRandom();
    StringBuilder primaryLines = new StringBuilder();
    List<CastVO.LineDetail> details = new ArrayList<>();
    List<Integer> changingLines = new ArrayList<>();

    // 自下而上逐爻起卦（position 1 = 初爻）
    for (int position = 1; position <= 6; position++) {
      int sum = 0;
      for (int coin = 0; coin < 3; coin++) {
        sum += random.nextBoolean() ? 3 : 2; // 字面算 3，背面算 2
      }
      boolean yang = (sum == 7 || sum == 9);       // 少阳/老阳为阳
      boolean changing = (sum == 6 || sum == 9);   // 老阴/老阳为变爻
      String label = switch (sum) {
        case 6 -> "老阴";
        case 7 -> "少阳";
        case 8 -> "少阴";
        default -> "老阳";
      };
      primaryLines.append(yang ? '1' : '0');
      details.add(new CastVO.LineDetail(position, label, yang, changing));
      if (changing) {
        changingLines.add(position);
      }
    }

    // 按六爻组合反查本卦
    BaguaHexagram primary = findByLines(primaryLines.toString());

    // 有变爻：翻转变爻阴阳得之卦；无变爻：之卦为 null
    HexagramVO changed = null;
    if (!changingLines.isEmpty()) {
      char[] changedChars = primaryLines.toString().toCharArray();
      for (int position : changingLines) {
        int idx = position - 1; // position 从 1 开始，数组从 0 开始
        changedChars[idx] = (changedChars[idx] == '1') ? '0' : '1';
      }
      changed = HexagramVO.of(findByLines(new String(changedChars)));
    }

    return CastVO.of(HexagramVO.of(primary), changed, changingLines, details);
  }

  /**
   * 每日一卦：同一星座同一天恒定（无变爻概念，直接定一卦），
   * 不传星座则是"今日通用卦"
   */
  public HexagramVO daily(String signQuery) {
    Long signId = resolveSignIdOrZero(signQuery);
    LocalDate today = LocalDate.now();

    List<BaguaHexagram> all = baguaHexagramRepository.findAll().stream()
        .sorted(Comparator.comparingInt(BaguaHexagram::getSeqNumber))
        .toList();
    Random random = fortuneGenerator.newSeededRandom(signId, today, BAGUA_DAILY_SALT);
    return HexagramVO.of(all.get(random.nextInt(all.size())));
  }

  /**
   * 按六爻组合查卦，查不到说明种子数据有问题，属于系统级错误
   */
  private BaguaHexagram findByLines(String lines) {
    return baguaHexagramRepository.findByLines(lines)
        .orElseThrow(() -> new IllegalStateException("卦库数据异常：找不到 lines=" + lines + " 的卦"));
  }

  /**
   * 解析星座参数：传了就必须认识（英文名忽略大小写或中文名），没传返回 0 表示"通用"
   */
  private Long resolveSignIdOrZero(String signQuery) {
    if (signQuery == null || signQuery.isBlank()) {
      return 0L;
    }
    String query = signQuery.trim();
    return signRepository.findByNameEnIgnoreCase(query)
        .or(() -> signRepository.findByName(query))
        .map(Sign::getId)
        .orElseThrow(() -> new IllegalArgumentException("不认识的星座：" + query + "（支持英文名或中文名，如 aries / 白羊座）"));
  }
}
