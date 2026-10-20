package com.starwhisper.server;

import com.starwhisper.server.service.WechatMpCodeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 小程序码 scene 清洗规则
 */
class MpCodeSceneTests {

  @Test
  void 合法字符原样保留() {
    assertEquals("leo", WechatMpCodeService.sanitize("leo"));
    assertEquals("sign=leo&src=poster", WechatMpCodeService.sanitize("sign=leo&src=poster"));
  }

  @Test
  void 非法字符剔除与超长截断() {
    assertEquals("leo", WechatMpCodeService.sanitize("leo座"));
    assertEquals("ab", WechatMpCodeService.sanitize("a b"));
    String long40 = "a".repeat(40);
    assertEquals(32, WechatMpCodeService.sanitize(long40).length());
    assertEquals("", WechatMpCodeService.sanitize(null));
    assertEquals("", WechatMpCodeService.sanitize(""));
  }
}
