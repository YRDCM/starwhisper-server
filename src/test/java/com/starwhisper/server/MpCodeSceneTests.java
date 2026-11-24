package com.starwhisper.server;

import com.starwhisper.server.service.WechatMpCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 官方小程序码静态服务：读文件 → base64 → 内存缓存
 */
class MpCodeSceneTests {

  @Test
  void 读取静态码并缓存(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("official.jpg");
    byte[] fake = {1, 2, 3, 4, 5};
    Files.write(file, fake);

    WechatMpCodeService service = new WechatMpCodeService(file.toString());
    String b64 = service.mpCodeBase64("leo");
    assertArrayEquals(fake, Base64.getDecoder().decode(b64));
    // 第二次调用走缓存，结果一致（scene 不影响官方静态码）
    assertEquals(b64, service.mpCodeBase64("aries"));
  }

  @Test
  void 文件缺失报错供调用方降级(@TempDir Path dir) {
    WechatMpCodeService service = new WechatMpCodeService(dir.resolve("missing.jpg").toString());
    assertThrows(IllegalStateException.class, () -> service.mpCodeBase64(""));
  }
}
