package com.starwhisper.server.repository;

import com.starwhisper.server.entity.HoroscopeCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ShowAPI 缓存数据访问层
 */
public interface HoroscopeCacheRepository extends JpaRepository<HoroscopeCache, Long> {

  // 按缓存 key 查（库表上有唯一索引，见 Flyway V2）
  Optional<HoroscopeCache> findByCacheKey(String cacheKey);

  // 定时清理：删掉 N 天前的缓存行（缓存 key 里带日期，旧行永远不会再被读到）
  int deleteByCreatedAtBefore(LocalDateTime time);
}
