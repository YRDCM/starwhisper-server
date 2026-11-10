package com.starwhisper.server;

import com.starwhisper.server.common.UsageTrackInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用埋点：URI → 板块映射（只记用户主动使用功能的接口）
 */
class UsageTrackTests {

  @Test
  void 功能接口正确映射板块() {
    assertEquals("fortune", UsageTrackInterceptor.moduleOf("GET", "/api/fortune/today"));
    assertEquals("fortune", UsageTrackInterceptor.moduleOf("GET", "/api/fortune/week"));
    assertEquals("fortune", UsageTrackInterceptor.moduleOf("GET", "/api/fortune/weekly"));
    assertEquals("match", UsageTrackInterceptor.moduleOf("POST", "/api/match"));
    assertEquals("match", UsageTrackInterceptor.moduleOf("GET", "/api/match")); // 实际是 GET 接口
    assertEquals("tarot", UsageTrackInterceptor.moduleOf("POST", "/api/tarot/draw"));
    assertEquals("tarot", UsageTrackInterceptor.moduleOf("GET", "/api/tarot/daily"));
    assertEquals("bagua", UsageTrackInterceptor.moduleOf("POST", "/api/bagua/cast"));
    assertEquals("hexagram", UsageTrackInterceptor.moduleOf("GET", "/api/hexagram/today"));
    assertEquals("ai", UsageTrackInterceptor.moduleOf("POST", "/api/tarot/ai-reading"));
    assertEquals("ai", UsageTrackInterceptor.moduleOf("POST", "/api/bagua/ai-reading"));
    assertEquals("checkin", UsageTrackInterceptor.moduleOf("POST", "/api/checkin"));
  }

  @Test
  void 非功能接口与后台接口不记录() {
    assertNull(UsageTrackInterceptor.moduleOf("GET", "/api/admin/stats"));
    assertNull(UsageTrackInterceptor.moduleOf("POST", "/api/auth/wechat"));
    assertNull(UsageTrackInterceptor.moduleOf("GET", "/api/tarot/cards")); // 牌库浏览太噪不记
    assertNull(UsageTrackInterceptor.moduleOf("GET", "/api/user/me"));
    assertNull(UsageTrackInterceptor.moduleOf("GET", "/api/checkin/summary")); // 查询不算使用
    assertNull(UsageTrackInterceptor.moduleOf("DELETE", "/api/match")); // 方法不匹配
    assertNull(UsageTrackInterceptor.moduleOf("GET", null));
  }
}
