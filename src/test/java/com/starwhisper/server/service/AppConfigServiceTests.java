package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppConfig;
import com.starwhisper.server.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 参数配置服务：白名单 / 默认值 / 缓存
 */
class AppConfigServiceTests {

  private final AppConfigRepository repository = mock(AppConfigRepository.class);

  private AppConfigService service() {
    return new AppConfigService(repository);
  }

  @Test void listReturnsWhitelistDefaultsWhenDatabaseIsEmpty() {
    when(repository.findAll()).thenReturn(List.of());
    var items = service().list();
    assertEquals(2, items.size());
    assertEquals(AppConfigService.KEY_AI_PER_IP, items.get(0).get("key"));
    assertEquals("3", items.get(0).get("value"));
    assertEquals(AppConfigService.KEY_AI_GLOBAL, items.get(1).get("key"));
    assertEquals("100", items.get(1).get("value"));
    assertNull(items.get(0).get("updatedAt")); // 从未保存过
    assertNotNull(items.get(0).get("desc"));
  }

  @Test void listPrefersStoredValuesOverDefaults() {
    when(repository.findAll()).thenReturn(List.of(
        new AppConfig(AppConfigService.KEY_AI_GLOBAL, "42", LocalDateTime.now())));
    var items = service().list();
    assertEquals("3", items.get(0).get("value"));   // 没存的仍走默认
    assertEquals("42", items.get(1).get("value"));  // 存过的用 DB 值
    assertNotNull(items.get(1).get("updatedAt"));
  }

  @Test void updateRejectsKeysOutsideWhitelist() {
    var service = service();
    assertThrows(IllegalArgumentException.class, () -> service.update("kimi.apiKey", "sk-xxx"));
    assertThrows(IllegalArgumentException.class, () -> service.update("ADMIN_KEY", "whatever"));
    assertThrows(IllegalArgumentException.class, () -> service.update(null, "1"));
    verify(repository, never()).save(any());
  }

  @Test void updatePersistsWhitelistedKeyAndInvalidatesCache() {
    when(repository.findAll()).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service = service();

    // 先读一次让缓存装填（空库 → 默认值）
    assertEquals(3, service.getInt(AppConfigService.KEY_AI_PER_IP, 3));

    var saved = service.update(AppConfigService.KEY_AI_PER_IP, "9");
    assertEquals("9", saved.get("value"));
    assertNotNull(saved.get("updatedAt"));
    verify(repository).save(argThat(c ->
        AppConfigService.KEY_AI_PER_IP.equals(c.getConfigKey()) && "9".equals(c.getConfigValue())));

    // update 后缓存必须已失效：下一次读会重新查库拿到新值
    when(repository.findAll()).thenReturn(List.of(
        new AppConfig(AppConfigService.KEY_AI_PER_IP, "9", LocalDateTime.now())));
    assertEquals(9, service.getInt(AppConfigService.KEY_AI_PER_IP, 3));
  }

  @Test void updateRejectsBlankValue() {
    var service = service();
    assertThrows(IllegalArgumentException.class, () -> service.update(AppConfigService.KEY_AI_PER_IP, "  "));
    verify(repository, never()).save(any());
  }

  @Test void getIntFallsBackOnMissingOrMalformedValues() {
    when(repository.findAll()).thenReturn(List.of(
        new AppConfig(AppConfigService.KEY_AI_PER_IP, "abc", LocalDateTime.now()),
        new AppConfig(AppConfigService.KEY_AI_GLOBAL, "-7", LocalDateTime.now())));
    var service = new AppConfigService(repository, 0);
    assertEquals(3, service.getInt(AppConfigService.KEY_AI_PER_IP, 3));   // 非数字 → 默认
    assertEquals(0, service.getInt(AppConfigService.KEY_AI_GLOBAL, 100)); // 负数 → 钳到 0
  }

  @Test void cacheAvoidsRepeatedDatabaseHitsWithinTtl() {
    when(repository.findAll()).thenReturn(List.of(
        new AppConfig(AppConfigService.KEY_AI_PER_IP, "5", LocalDateTime.now())));
    var service = new AppConfigService(repository, 60_000);
    service.getInt(AppConfigService.KEY_AI_PER_IP, 3);
    service.getInt(AppConfigService.KEY_AI_PER_IP, 3);
    service.getInt(AppConfigService.KEY_AI_PER_IP, 3);
    verify(repository, times(1)).findAll(); // TTL 内只查一次库
  }

  @Test void zeroTtlAlwaysReadsDatabase() {
    when(repository.findAll()).thenReturn(List.of());
    var service = new AppConfigService(repository, 0);
    service.getInt(AppConfigService.KEY_AI_PER_IP, 3);
    service.getInt(AppConfigService.KEY_AI_PER_IP, 3);
    verify(repository, times(2)).findAll();
  }

  @Test void isEditableOnlyForWhitelist() {
    var service = service();
    assertTrue(service.isEditable(AppConfigService.KEY_AI_PER_IP));
    assertTrue(service.isEditable(AppConfigService.KEY_AI_GLOBAL));
    assertFalse(service.isEditable("spring.datasource.password"));
    assertFalse(service.isEditable(null));
  }
}
