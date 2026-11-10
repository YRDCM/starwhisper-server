package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppConfig;
import com.starwhisper.server.repository.AppConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运行期参数配置（后台「参数配置」模块，v5）
 *
 * 设计要点：
 * - 白名单制：只有 EDITABLE 里登记过的 key 才能读写，杜绝把密钥塞进 app_config 表
 * - 默认值内置：DB 没有记录时用默认值，表可以是空的也能跑
 * - 小缓存：读路径（AiDailyQuota 每次校验都会调 getInt）走 5 秒内存缓存，
 *   避免每个 AI 请求都查库；update 成功后立即失效缓存，改完即刻生效
 */
@Service
public class AppConfigService {

  /** 每 IP 每日 AI 解读次数 */
  public static final String KEY_AI_PER_IP = "ai.perIpDailyLimit";
  /** 全站每日 AI 解读次数 */
  public static final String KEY_AI_GLOBAL = "ai.globalDailyLimit";

  /** 可编辑配置项定义（key / 默认值 / 描述）；新增配置项在这里登记 */
  public record ConfigDef(String key, String defaultValue, String desc) {
  }

  private static final List<ConfigDef> EDITABLE = List.of(
      new ConfigDef(KEY_AI_PER_IP, "3", "每 IP 每日 AI 解读次数（塔罗解牌与易经解卦共用同一额度池）"),
      new ConfigDef(KEY_AI_GLOBAL, "100", "全站每日 AI 解读次数上限（所有用户合计，按自然日重置）"));

  private static final Map<String, ConfigDef> DEFS = EDITABLE.stream()
      .collect(Collectors.toMap(ConfigDef::key, Function.identity(), (a, b) -> a, LinkedHashMap::new));

  private static final long DEFAULT_CACHE_TTL_MS = 5000;

  private final AppConfigRepository repository;
  private final long cacheTtlMs;
  private final Object cacheLock = new Object();

  private volatile Map<String, String> cache = Map.of();
  private volatile long cacheLoadedAt = 0;

  @Autowired
  public AppConfigService(AppConfigRepository repository) {
    this(repository, DEFAULT_CACHE_TTL_MS);
  }

  /** 测试用：可指定缓存 TTL（传 0 关闭缓存，每次读都查库） */
  public AppConfigService(AppConfigRepository repository, long cacheTtlMs) {
    this.repository = repository;
    this.cacheTtlMs = Math.max(0, cacheTtlMs);
  }

  /** key 是否在白名单内（控制器据此返回 400） */
  public boolean isEditable(String key) {
    return key != null && DEFS.containsKey(key.trim());
  }

  /** 全部可编辑配置项：DB 有值用 DB 值，没有返回默认值；updatedAt 为 null 表示从未保存过 */
  public List<Map<String, Object>> list() {
    Map<String, AppConfig> stored = repository.findAll().stream()
        .collect(Collectors.toMap(AppConfig::getConfigKey, Function.identity()));
    List<Map<String, Object>> items = new ArrayList<>();
    for (ConfigDef def : EDITABLE) {
      AppConfig config = stored.get(def.key());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("key", def.key());
      item.put("value", config != null ? config.getConfigValue() : def.defaultValue());
      item.put("desc", def.desc());
      item.put("updatedAt", config != null ? config.getUpdatedAt() : null);
      items.add(item);
    }
    return items;
  }

  /**
   * 更新一个白名单内的配置项；非法 key 抛 IllegalArgumentException（控制器转 400）
   * 保存后立即失效缓存，AiDailyQuota 下一次校验就按新限额执行，不用重启
   */
  public Map<String, Object> update(String key, String value) {
    if (key == null || !DEFS.containsKey(key.trim())) {
      throw new IllegalArgumentException("不支持的配置项：" + key);
    }
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("配置值不能为空");
    }
    String trimmedKey = key.trim();
    AppConfig config = new AppConfig(trimmedKey, value.trim(), LocalDateTime.now());
    repository.save(config);
    invalidateCache();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("key", trimmedKey);
    item.put("value", config.getConfigValue());
    item.put("desc", DEFS.get(trimmedKey).desc());
    item.put("updatedAt", config.getUpdatedAt());
    return item;
  }

  /** 读字符串值；DB 没有记录返回 null（调用方自己决定默认值） */
  public String get(String key) {
    return cachedValues().get(key);
  }

  /** 读整数限额；DB 没记录或值不是合法整数时回落默认值，并保证不为负 */
  public int getInt(String key, int defaultValue) {
    String raw = cachedValues().get(key);
    if (!StringUtils.hasText(raw)) {
      return Math.max(0, defaultValue);
    }
    try {
      return Math.max(0, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      return Math.max(0, defaultValue);
    }
  }

  /** 立即失效缓存（update 后调用；测试也可用手动清缓存） */
  public void invalidateCache() {
    synchronized (cacheLock) {
      cache = Map.of();
      cacheLoadedAt = 0;
    }
  }

  /**
   * TTL 内直接返回缓存快照，过期才查库重建。
   * volatile 快照引用保证读线程无锁拿到一致视图；重建放锁内，避免惊群
   */
  private Map<String, String> cachedValues() {
    long now = System.currentTimeMillis();
    if (now - cacheLoadedAt < cacheTtlMs) {
      return cache;
    }
    synchronized (cacheLock) {
      if (System.currentTimeMillis() - cacheLoadedAt < cacheTtlMs) {
        return cache;
      }
      Map<String, String> fresh = new LinkedHashMap<>();
      repository.findAll().forEach(c -> fresh.put(c.getConfigKey(), c.getConfigValue()));
      cache = Map.copyOf(fresh);
      cacheLoadedAt = System.currentTimeMillis();
      return cache;
    }
  }
}
