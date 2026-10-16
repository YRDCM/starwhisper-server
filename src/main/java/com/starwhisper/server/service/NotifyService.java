package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.SubscribeRecord;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.SubscribeRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 订阅消息登记业务层
 *
 * 微信订阅消息的平台规则：一次授权只能推送一次。
 * 所以「订阅」在这里不是长期关系，而是一笔「待推送额度」：
 *   subscribe   → 登记为 ACTIVE（用户刚点过允许）
 *   推送成功    → 置为 CONSUMED（额度用完，想继续收要重新授权）
 *   unsubscribe → 置为 CANCELLED（用户主动取消）
 */
@Service
public class NotifyService {

  private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

  /** 目前支持的订阅场景（白名单校验，防止前端乱传脏数据） */
  private static final Set<String> SCENES = Set.of(SubscribeRecord.SCENE_DAILY_FORTUNE);

  private final SubscribeRecordRepository subscribeRecordRepository;
  private final AppUserRepository appUserRepository;

  public NotifyService(SubscribeRecordRepository subscribeRecordRepository,
                       AppUserRepository appUserRepository) {
    this.subscribeRecordRepository = subscribeRecordRepository;
    this.appUserRepository = appUserRepository;
  }

  /**
   * 订阅：用户在小程序里点了「允许」后调用，登记为待推送。
   * 幂等：重复订阅只刷新状态/时间，不会插重复记录（唯一约束兜底）。
   *
   * @param user 当前登录用户
   * @param scene 订阅场景（见 SubscribeRecord.SCENE_*）
   * @param sign  用户星座英文名（可选；传了就顺手更新到用户资料，推送时按它取运势）
   */
  public void subscribe(AppUser user, String scene, String sign) {
    String validScene = requireValidScene(scene);
    LocalDateTime now = LocalDateTime.now();

    SubscribeRecord record = subscribeRecordRepository
        .findByUserIdAndScene(user.getId(), validScene)
        .orElseGet(() -> new SubscribeRecord(user.getId(), validScene, SubscribeRecord.STATUS_ACTIVE, now));
    record.setStatus(SubscribeRecord.STATUS_ACTIVE);
    record.setUpdatedAt(now);
    subscribeRecordRepository.save(record);

    // 顺带记录用户星座（推送任务的运势内容依赖它）
    if (StringUtils.hasText(sign)) {
      user.setSignNameEn(sign.trim().toLowerCase());
      appUserRepository.save(user);
    }
    log.info("订阅登记：userId={}, scene={}", user.getId(), validScene);
  }

  /**
   * 退订：置为 CANCELLED；没订阅过也算成功（幂等，前端不用关心状态）
   */
  public void unsubscribe(AppUser user, String scene) {
    String validScene = requireValidScene(scene);
    subscribeRecordRepository.findByUserIdAndScene(user.getId(), validScene)
        .ifPresent(record -> {
          record.setStatus(SubscribeRecord.STATUS_CANCELLED);
          record.setUpdatedAt(LocalDateTime.now());
          subscribeRecordRepository.save(record);
          log.info("订阅取消：userId={}, scene={}", user.getId(), validScene);
        });
  }

  /**
   * 当前用户处于 ACTIVE 状态的订阅场景列表
   */
  public List<String> activeScenes(Long userId) {
    return subscribeRecordRepository
        .findByUserIdAndStatus(userId, SubscribeRecord.STATUS_ACTIVE)
        .stream()
        .map(SubscribeRecord::getScene)
        .toList();
  }

  private String requireValidScene(String scene) {
    if (!StringUtils.hasText(scene)) {
      throw new IllegalArgumentException("缺少参数 scene");
    }
    String normalized = scene.trim().toUpperCase();
    if (!SCENES.contains(normalized)) {
      throw new IllegalArgumentException("不支持的订阅场景：" + scene);
    }
    return normalized;
  }
}
