package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.Hexagram;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.HexagramRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 每日一卦（v4）
 * 确定性选取：同一天同一 openid 永远抽到同一卦；无 openid 按日期走通用卦
 */
@Service
public class HexagramService {

  private static final Logger log = LoggerFactory.getLogger(HexagramService.class);

  private final HexagramRepository hexagramRepository;
  private final AppUserRepository appUserRepository;
  private final UserHistoryService userHistoryService;

  public HexagramService(HexagramRepository hexagramRepository,
                         AppUserRepository appUserRepository,
                         UserHistoryService userHistoryService) {
    this.hexagramRepository = hexagramRepository;
    this.appUserRepository = appUserRepository;
    this.userHistoryService = userHistoryService;
  }

  /**
   * 今日一卦：GET /api/hexagram/today?openid=xxx
   * 选取规则：floorMod(日期积日 * 31 + openid.hashCode, 64) + 1，同一用户同一天结果固定；
   * 浏览计入 user_history（type=hexagram，openid 能查到用户才记）
   */
  public Map<String, Object> today(String openid) {
    LocalDate today = LocalDate.now();

    long seed = today.toEpochDay() * 31L
        + ((openid == null || openid.isBlank()) ? 0L : openid.trim().hashCode());
    int id = (int) Math.floorMod(seed, 64) + 1;

    Hexagram hexagram = hexagramRepository.findById(id)
        .orElseThrow(() -> new IllegalStateException("hexagram 种子数据缺失：id=" + id));

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("date", today.toString());
    data.put("name", hexagram.getName());
    data.put("symbol", hexagram.getSymbol());
    data.put("guaCi", hexagram.getGuaCi());
    data.put("interpretation", hexagram.getInterpretation());
    data.put("advice", hexagram.getAdvice());
    data.put("luckLevel", hexagram.getLuckLevel());

    // 浏览计入历史：openid 对应到用户才记，游客只看不记；记录失败不影响主流程
    if (openid != null && !openid.isBlank()) {
      Optional<AppUser> user = appUserRepository.findByOpenid(openid.trim());
      user.ifPresent(u -> userHistoryService.record(u, "hexagram",
          "每日一卦 · " + hexagram.getName(), data));
    }
    return data;
  }
}
