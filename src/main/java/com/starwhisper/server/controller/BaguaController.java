package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.CastVO;
import com.starwhisper.server.dto.HexagramVO;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.BaguaService;
import com.starwhisper.server.service.HexagramAiService;
import com.starwhisper.server.service.KimiTarotClient;
import com.starwhisper.server.service.UserHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 易经六十四卦相关接口
 */
@RestController
@RequestMapping("/api/bagua")
public class BaguaController {

  private final BaguaService baguaService;
  private final UserHistoryService userHistoryService;
  private final HexagramAiService hexagramAi;

  // 构造注入
  public BaguaController(BaguaService baguaService, UserHistoryService userHistoryService, HexagramAiService hexagramAi) {
    this.baguaService = baguaService;
    this.userHistoryService = userHistoryService;
    this.hexagramAi = hexagramAi;
  }

  /**
   * 卦库一览：GET /api/bagua/hexagrams
   */
  @GetMapping("/hexagrams")
  public Result<List<HexagramVO>> hexagrams() {
    return Result.success(baguaService.listHexagrams());
  }

  /**
   * 三枚铜钱法起卦：POST /api/bagua/cast
   * 登录用户会自动记一条占卜历史（游客起卦不记）
   */
  @PostMapping("/cast")
  public Result<CastVO> cast(HttpServletRequest request) {
    CastVO cast = baguaService.cast();

    // 登录用户记历史；记录失败不影响起卦（service 内部已兜底）
    AppUser user = CurrentUser.get(request);
    if (user != null) {
      userHistoryService.record(user, "BAGUA", buildCastTitle(cast), cast);
    }
    return Result.success(cast);
  }

  /**
   * 每日一卦：GET /api/bagua/daily?sign=aries
   * sign 可选，支持英文名或中文名；不传则是今日通用卦
   */
  @GetMapping("/daily")
  public Result<HexagramVO> daily(@RequestParam(required = false) String sign) {
    return Result.success(baguaService.daily(sign));
  }

  /**
   * AI 解卦请求：卦面身份（本卦卦序 + 可选之卦卦序 + 变爻位置）+ 可选用户问题。
   * 卦辞爻义由后端从卦库取出组装，前端传的卦序只作定位，不接受自由文本卦辞。
   */
  public record AiRequest(Integer primarySeq, Integer changedSeq, List<Integer> changingLines, String question) {}

  /**
   * Kimi AI 解卦：POST /api/bagua/ai-reading
   * 限额与塔罗解牌共用（每 IP 3 次/天 + 全站 100 次/天），响应结构同为 { summary, detail }
   */
  @PostMapping("/ai-reading")
  public Result<KimiTarotClient.Reading> aiReading(@RequestBody(required = false) AiRequest body,
                                                   HttpServletRequest request) {
    if (body == null || body.primarySeq() == null) {
      // 之前这里直接 body.primarySeq()，请求体为空时会抛 NPE 变成 500
      throw new IllegalArgumentException("缺少参数：primarySeq（本卦卦序 1-64）");
    }
    return Result.success(hexagramAi.read(body.primarySeq(), body.changedSeq(), body.changingLines(),
        body.question(), request.getRemoteAddr()));
  }

  /**
   * 历史标题：铜钱起卦 · 雷水解 → 雷地豫（无变爻则没有箭头后半段）
   */
  private String buildCastTitle(CastVO cast) {
    String title = "铜钱起卦 · " + cast.getPrimary().getName();
    if (cast.getChanged() != null) {
      title += " → " + cast.getChanged().getName();
    }
    return title;
  }
}
