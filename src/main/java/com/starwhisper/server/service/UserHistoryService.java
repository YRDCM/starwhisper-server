package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.UserHistory;
import com.starwhisper.server.repository.UserHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 占卜历史记录服务
 * 原则：记录失败绝不能拖垮主流程（抽牌/起卦），所以这里整体 try-catch 只记日志
 */
@Service
public class UserHistoryService {

  private static final Logger log = LoggerFactory.getLogger(UserHistoryService.class);

  private final UserHistoryRepository userHistoryRepository;
  private final ObjectMapper objectMapper; // Spring Boot 4 自动配置的 Jackson 3 ObjectMapper

  public UserHistoryService(UserHistoryRepository userHistoryRepository, ObjectMapper objectMapper) {
    this.userHistoryRepository = userHistoryRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * 记录一条占卜历史
   *
   * @param user   当前登录用户
   * @param type   TAROT / BAGUA
   * @param title  摘要标题
   * @param detail 结果对象，会被序列化成 JSON 字符串存库
   */
  public void record(AppUser user, String type, String title, Object detail) {
    try {
      UserHistory history = new UserHistory();
      history.setUserId(user.getId());
      history.setType(type);
      history.setTitle(title);
      history.setDetail(objectMapper.writeValueAsString(detail));
      history.setCreatedAt(LocalDateTime.now());
      userHistoryRepository.save(history);
    } catch (Exception e) {
      log.warn("历史记录保存失败（不影响主流程）：{}", e.getMessage());
    }
  }
}
