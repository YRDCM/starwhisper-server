package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.DrawResultVO;
import com.starwhisper.server.dto.DrawnCardVO;
import com.starwhisper.server.dto.TarotCardVO;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.TarotService;
import com.starwhisper.server.service.TarotSpread;
import com.starwhisper.server.service.UserHistoryService;
import com.starwhisper.server.service.TarotAiService;
import com.starwhisper.server.service.KimiTarotClient;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 塔罗相关接口
 */
@RestController
@RequestMapping("/api/tarot")
public class TarotController {

  private final TarotService tarotService;
  private final UserHistoryService userHistoryService;
  private final TarotAiService ai;

  // 构造注入
  public TarotController(TarotService tarotService, UserHistoryService userHistoryService, TarotAiService ai) {
    this.tarotService = tarotService;
    this.userHistoryService = userHistoryService;
    this.ai = ai;
  }

  /**
   * 牌库一览：GET /api/tarot/cards
   */
  @GetMapping("/cards")
  public Result<List<TarotCardVO>> cards() {
    return Result.success(tarotService.listCards());
  }

  /**
   * 抽牌：POST /api/tarot/draw
   * 新参数 spread=single/three/choice/love/celtic/hexagram；
   * 兼容老参数 count（1→single，3→three），都不传等同 single
   * 可选参数 cardNos：逗号分隔的牌序号（1-78，用户暗置选牌），
   * 数量必须等于牌阵张数；不传则保持纯随机（旧行为）
   * 登录用户会自动记一条占卜历史（游客抽牌不记）
   */
  @PostMapping("/draw")
  public Result<DrawResultVO> draw(@RequestParam(required = false) String spread,
                                   @RequestParam(required = false) Integer count,
                                   @RequestParam(required = false) String cardNos,
                                   HttpServletRequest request) {
    DrawResultVO result = tarotService.draw(resolveSpread(spread, count), parseCardNos(cardNos));
    result.setDrawId(ai.register(result));

    // 登录用户记历史；记录失败不影响抽牌（service 内部已兜底）
    AppUser user = CurrentUser.get(request);
    if (user != null) {
      userHistoryService.record(user, "TAROT", buildDrawTitle(result), result);
    }
    return Result.success(result);
  }

  /**
   * 解析 cardNos：逗号分隔的 1-78 序号；没传返回 null（走随机）；
   * 格式非法直接报清楚的错（数量/越界/重复在 service 层校验）
   */
  private List<Integer> parseCardNos(String cardNos) {
    if (cardNos == null || cardNos.isBlank()) {
      return null;
    }
    List<Integer> nos = new ArrayList<>();
    for (String part : cardNos.split(",", -1)) {
      String token = part.trim();
      try {
        nos.add(Integer.valueOf(token));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("cardNos 格式错误：「" + token + "」不是数字（应为逗号分隔的牌序号，如 1,22,37）");
      }
    }
    return nos;
  }

  public record AiRequest(String drawId, String question) {}

  @PostMapping("/ai-reading")
  public Result<KimiTarotClient.Reading> aiReading(@RequestBody(required = false) AiRequest body,
                                                   HttpServletRequest request) {
    if (body == null) {
      throw new IllegalArgumentException("缺少请求体（drawId 与 question）");
    }
    AppUser user = CurrentUser.get(request);
    Long userId = (user == null) ? null : user.getId();
    return Result.success(ai.read(body.drawId(), body.question(), request.getRemoteAddr(), userId));
  }

  /**
   * 解析牌阵参数：spread 优先；否则走老的 count 参数（保持旧行为）；
   * 都不传默认 single（旧版 count 默认 1）
   */
  private TarotSpread resolveSpread(String spread, Integer count) {
    if (spread != null && !spread.isBlank()) {
      return TarotSpread.fromKey(spread);
    }
    int c = (count == null) ? 1 : count;
    if (c == 1) {
      return TarotSpread.SINGLE;
    }
    if (c == 3) {
      return TarotSpread.THREE;
    }
    throw new IllegalArgumentException("抽牌数量只支持 1 张或 3 张，收到：" + c);
  }

  /**
   * 历史标题：牌阵名 + 牌面，如「时间之流 · 宝剑八(正)/圣杯三(逆)/星币九(正)」
   */
  private String buildDrawTitle(DrawResultVO result) {
    String cards = result.getCards().stream()
        .map(d -> d.getCard().getName() + ("upright".equals(d.getOrientation()) ? "(正)" : "(逆)"))
        .collect(Collectors.joining("/"));
    return result.getSpreadName() + " · " + cards;
  }

  /**
   * 每日一牌：GET /api/tarot/daily?sign=aries
   * sign 可选，支持英文名或中文名；不传则是今日通用牌
   */
  @GetMapping("/daily")
  public Result<DrawnCardVO> daily(@RequestParam(required = false) String sign) {
    return Result.success(tarotService.daily(sign));
  }
}
