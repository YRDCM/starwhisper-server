package com.starwhisper.server.service;

import com.starwhisper.server.entity.HoroscopeCache;
import com.starwhisper.server.repository.HoroscopeCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ShowAPI 缓存读写
 *
 * 原则：缓存是省配额的优化项，自身任何异常都不能影响主流程（全部兜底返回空 / 静默失败）。
 *
 * 并发说明：写缓存是「先查再写」，两个请求同时首次写入同一个 key 时，
 * 后写的那个会撞上 cache_key 的唯一索引。这里捕获冲突后改为更新已有行，
 * 而不是把异常抛给调用方 —— 之前这个异常会被 getToday 静默吞掉，
 * 表现成"缓存莫名其妙不生效，而且表里越积越多重复行"。
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
    return get(cacheKey, true);
  }

  /**
   * 按 key 精确读取：不看写入日期。
   * 给周运这类"key 本身带周期标识"（如 leo:week:2026-10-12）的缓存用——
   * 跨周天然换 key，不存在过期串周问题
   */
  public Optional<String> getByKey(String cacheKey) {
    return get(cacheKey, false);
  }

  private Optional<String> get(String cacheKey, boolean mustBeToday) {
    try {
      return repository.findByCacheKey(cacheKey)
          .filter(cache -> !mustBeToday || (cache.getCreatedAt() != null
              && cache.getCreatedAt().toLocalDate().equals(LocalDate.now())))
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
    } catch (DataIntegrityViolationException e) {
      // 并发首次写入撞唯一键：改成更新已存在的那一行
      log.debug("缓存并发写入冲突，改为更新已有行：key={}", cacheKey);
      try {
        repository.findByCacheKey(cacheKey).ifPresent(existing -> {
          existing.setContent(content);
          existing.setCreatedAt(LocalDateTime.now());
          repository.save(existing);
        });
      } catch (Exception inner) {
        log.warn("运势缓存冲突后更新失败（不影响主流程）：key={}, 原因={}", cacheKey, inner.getMessage());
      }
    } catch (Exception e) {
      log.warn("运势缓存写入失败（不影响主流程）：key={}, 原因={}", cacheKey, e.getMessage());
    }
  }

  /** 定时清理：删除 N 天前的缓存行，返回删除条数 */
  public int cleanupOlderThan(LocalDate cutoff) {
    try {
      return repository.deleteByCreatedAtBefore(cutoff.atStartOfDay());
    } catch (Exception e) {
      log.warn("缓存清理失败：cutoff={}, 原因={}", cutoff, e.getMessage());
      return 0;
    }
  }
}
