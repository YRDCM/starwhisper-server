package com.starwhisper.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AI 限额动态化：限额每次校验实时从 AppConfigService 读，
 * 后台改配置立即生效；无配置源（两参构造）时行为与旧版固定限额一致
 */
class AiDailyQuotaDynamicTests {

  private final AppConfigService config = mock(AppConfigService.class);

  /** stub 两个限额 key（Mockito 对未 stub 的 int 返回 0，两个都必须给） */
  private void limits(int perIp, int global) {
    when(config.getInt(eq(AppConfigService.KEY_AI_PER_IP), anyInt())).thenReturn(perIp);
    when(config.getInt(eq(AppConfigService.KEY_AI_GLOBAL), anyInt())).thenReturn(global);
  }

  @Test void limitsAreReadFromConfigOnEveryCheck() {
    limits(1, 100);
    var quota = new AiDailyQuota(1, 100, config);
    quota.consume("ip1");
    assertThrows(IllegalStateException.class, () -> quota.consume("ip1")); // 每 IP 1 次已用完

    // 后台把每 IP 限额调成 2：不重启、不重建对象，下一次校验立即生效
    limits(2, 100);
    assertDoesNotThrow(() -> quota.consume("ip1"));
    assertThrows(IllegalStateException.class, () -> quota.consume("ip1"));
  }

  @Test void globalLimitAlsoFollowsConfig() {
    limits(10, 1);
    var quota = new AiDailyQuota(3, 100, config);
    quota.consume("a");
    assertThrows(IllegalStateException.class, () -> quota.consume("b"));

    limits(10, 2); // 全站名额放宽到 2
    assertDoesNotThrow(() -> quota.consume("b"));
    assertThrows(IllegalStateException.class, () -> quota.consume("c"));
  }

  @Test void missingConfigServiceFallsBackToConstructorDefaults() {
    var quota = new AiDailyQuota(1, 2); // 旧的两参构造：固定限额
    quota.consume("a");
    assertThrows(IllegalStateException.class, () -> quota.consume("a"));
    quota.consume("b");
    assertThrows(IllegalStateException.class, () -> quota.consume("c"));
  }

  @Test void readonlyCountersReportUsageAndRemaining() {
    limits(3, 5);
    var quota = new AiDailyQuota(3, 100, config);
    assertEquals(0, quota.todayUsed());
    assertEquals(5, quota.globalRemaining());
    assertEquals(5, quota.globalLimit());

    quota.consume("a");
    quota.consume("b");
    assertEquals(2, quota.todayUsed());
    assertEquals(3, quota.globalRemaining());

    // 限额调小后 remaining 不能变负数
    limits(3, 1);
    assertEquals(0, quota.globalRemaining());
    assertEquals(1, quota.globalLimit());
  }

  @Test void perIpLimitReadsConfiguredValue() {
    limits(7, 100);
    var quota = new AiDailyQuota(3, 100, config);
    assertEquals(7, quota.perIpLimit());
  }
}
