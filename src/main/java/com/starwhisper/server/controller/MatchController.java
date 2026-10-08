package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.dto.MatchVO;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.service.MatchService;
import com.starwhisper.server.service.UserHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 星座配对接口
 */
@RestController
@RequestMapping("/api/match")
public class MatchController {

  private final MatchService matchService;
  private final UserHistoryService userHistoryService;

  // 构造注入
  public MatchController(MatchService matchService, UserHistoryService userHistoryService) {
    this.matchService = matchService;
    this.userHistoryService = userHistoryService;
  }

  /**
   * 配对：GET /api/match?star1=libra&star2=leo&gender1=1&gender2=0
   * star 支持英文名（忽略大小写）或中文名；gender 1=男 0=女，默认 1/0
   * 登录用户会自动记一条历史（游客配对不记）
   */
  @GetMapping
  public Result<MatchVO> match(@RequestParam String star1,
                               @RequestParam String star2,
                               @RequestParam(defaultValue = "1") int gender1,
                               @RequestParam(defaultValue = "0") int gender2,
                               HttpServletRequest request) {
    MatchVO vo = matchService.match(star1, star2, gender1, gender2);

    // 登录用户记历史；记录失败不影响配对（service 内部已兜底）
    AppUser user = CurrentUser.get(request);
    if (user != null) {
      userHistoryService.record(user, "MATCH", buildMatchTitle(vo), vo);
    }
    return Result.success(vo);
  }

  /**
   * 历史标题：星座配对 · 天秤座 × 狮子座 · 90分（综合指数缺失时省略分数段）
   */
  private String buildMatchTitle(MatchVO vo) {
    String title = "星座配对 · " + vo.getStar1().getName() + " × " + vo.getStar2().getName();
    if (vo.getScores() != null && vo.getScores().getOverall() != null) {
      title += " · " + vo.getScores().getOverall() + "分";
    }
    return title;
  }
}
