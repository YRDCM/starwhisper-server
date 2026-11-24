package com.starwhisper.server.controller;

import com.starwhisper.server.common.CurrentUser;
import com.starwhisper.server.common.Result;
import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.Feedback;
import com.starwhisper.server.repository.FeedbackRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户反馈接口。
 *
 * **游客也能提交**（不需要登录）——这条很重要：
 * 这个小程序此前没有任何反馈通道（昵称是系统生成的、订阅模板为空、页面没有客服入口），
 * 等于"能看到数字但问不到人"。有了这个接口，至少能收到一句话。
 *
 * 限频：同一 IP 每天最多 10 条，避免被灌。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

  private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

  private static final int MAX_CONTENT = 500;
  private static final int MAX_CONTACT = 128;
  private static final int MAX_PAGE = 64;
  private static final int PER_IP_DAILY = 10;

  private final FeedbackRepository feedbackRepository;

  public FeedbackController(FeedbackRepository feedbackRepository) {
    this.feedbackRepository = feedbackRepository;
  }

  /**
   * 提交反馈：POST /api/feedback
   * body {"content":"...", "contact":"可选", "page":"可选，如 tarot"}
   */
  @PostMapping
  public Result<Map<String, Object>> submit(@RequestBody(required = false) Map<String, String> body,
                                            HttpServletRequest request) {
    String raw = body == null ? null : body.get("content");
    if (!StringUtils.hasText(raw)) {
      throw new IllegalArgumentException("说点什么再提交吧");
    }
    String content = raw.trim();
    if (content.length() < 2) {
      throw new IllegalArgumentException("内容太短了，多写几个字吧");
    }
    if (content.length() > MAX_CONTENT) {
      throw new IllegalArgumentException("反馈最多 " + MAX_CONTENT + " 个字");
    }

    String ip = request.getRemoteAddr();
    LocalDateTime dayStart = LocalDate.now().atStartOfDay();
    long todayCount = feedbackRepository.countByIpAndCreatedAtAfter(ip, dayStart);
    if (todayCount >= PER_IP_DAILY) {
      throw new IllegalStateException("今天已经收到你很多条反馈了，明天再聊");
    }

    AppUser user = CurrentUser.get(request);

    Feedback feedback = new Feedback(
        user == null ? null : user.getId(),
        user == null ? null : user.getOpenid(),
        ip,
        truncate(body.get("page"), MAX_PAGE),
        content,
        truncate(body.get("contact"), MAX_CONTACT),
        LocalDateTime.now());
    feedbackRepository.save(feedback);

    log.info("收到用户反馈：userId={}, 长度={}, page={}",
        feedback.getUserId(), content.length(), feedback.getPage());

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ok", true);
    data.put("todayLeft", Math.max(0, PER_IP_DAILY - todayCount - 1));
    return Result.ok0(data);
  }

  private static String truncate(String value, int max) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
