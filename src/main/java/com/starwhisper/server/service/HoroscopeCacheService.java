package com.starwhisper.server.service;

import com.starwhisper.server.entity.HoroscopeCache;
import com.starwhisper.server.repository.HoroscopeCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ShowAPI 缓存读写
 * 原则：缓存是省配额的优化项，自身任何异常都不能影响主流程（全部兜底返回空/静默）
 */
@Service
public class HoroscopeCacheService {

  private static final Logger log = LoggerFactory.getLogger(HoroscopeCacheService.class);

  private final HoroscopeCacheRepository repository;

  public HoroscopeCacheService(HoroscopeCacheRepository repository) {
    this.repository = repository;
  }

  /**
   * 查当天缓存：命中且写入时间就是今天才返回内容，否则空（让调用方去调外部接口）
   */
  public Optional<String> getToday(String cacheKey) {
    try {
      return repository.findByCacheKey(cacheKey)
          .filter(cache -> cache.getCreatedAt() != null
              && cache.getCreatedAt().toLocalDate().equals(LocalDate.now()))
          .map(HoroscopeCache::getContent)
          .filter(content -> content != null && !content.isBlank());
    } catch (Exception e) {
      log.warn("运势缓存读取失败（当作未命中）：key={}, 原因={}", cacheKey, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 写缓存：同 key 覆盖（upsert），失败只记日志
   */
  public void put(String cacheKey, String content) {
    try {
      HoroscopeCache cache = repository.findByCacheKey(cacheKey).orElseGet(HoroscopeCache::new);
      cache.setCacheKey(cacheKey);
      cache.setContent(content);
      cache.setCreatedAt(LocalDateTime.now());
      repository.save(cache);
    } catch (Exception e) {
      log.warn("运势缓存写入失败（不影响主流程）：key={}, 原因={}", cacheKey, e.getMessage());
    }
  }
}
