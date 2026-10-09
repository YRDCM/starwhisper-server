package com.starwhisper.server.repository;

import com.starwhisper.server.entity.HoroscopeCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ShowAPI 缓存数据访问层
 */
public interface HoroscopeCacheRepository extends JpaRepository<HoroscopeCache, Long> {

  // 按缓存 key 查（唯一索引）
  Optional<HoroscopeCache> findByCacheKey(String cacheKey);
}
