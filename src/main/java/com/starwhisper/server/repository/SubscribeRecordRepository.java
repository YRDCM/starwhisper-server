package com.starwhisper.server.repository;

import com.starwhisper.server.entity.SubscribeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 订阅消息登记数据访问层
 */
public interface SubscribeRecordRepository extends JpaRepository<SubscribeRecord, Long> {

  // 某用户某场景的订阅记录（唯一约束，最多一条）
  Optional<SubscribeRecord> findByUserIdAndScene(Long userId, String scene);

  // 某用户指定状态的全部订阅（状态接口用：查 ACTIVE）
  List<SubscribeRecord> findByUserIdAndStatus(Long userId, String status);

  // 某场景指定状态的全部订阅（推送任务用：查 ACTIVE）
  List<SubscribeRecord> findBySceneAndStatus(String scene, String status);

  // 后台统计：指定状态的订阅数量（subscribeActive 指标）
  long countByStatus(String status);
}
