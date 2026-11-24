package com.starwhisper.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 小程序码服务（海报用）：直接读服务器上的官方小程序码静态图（公众平台后台下载的正式版码），
 * 返回 base64 给小程序端绘制海报。
 * 不调用微信 getwxacode 接口——微信网关会拦截 Java POST（412 空 body），静态码稳定无依赖。
 * 读取后缓存内存，文件变化需重启或清除缓存（静态码本身不会变）。
 */
@Service
public class WechatMpCodeService {

  private static final Logger log = LoggerFactory.getLogger(WechatMpCodeService.class);

  private final Path codeFile;
  private volatile byte[] cached;

  public WechatMpCodeService(@Value("${wx.mpcode.file:/opt/starwhisper/mpcode/official.jpg}") String codeFile) {
    this.codeFile = Path.of(codeFile);
  }

  /**
   * 取小程序码图片的 base64。scene 参数保留（官方码不带 scene，当前忽略）
   * 文件缺失/读取失败抛 IllegalStateException，调用方降级（海报回退占位框）
   */
  public String mpCodeBase64(String scene) {
    byte[] bytes = cached;
    if (bytes == null) {
      synchronized (this) {
        if (cached == null) {
          try {
            cached = Files.readAllBytes(codeFile);
            log.info("官方小程序码已加载：{}（{} 字节）", codeFile, cached.length);
          } catch (IOException e) {
            // 绝对路径只进日志，不回传给客户端（避免泄露服务器目录结构）
            log.warn("小程序码文件不可读：{}", codeFile, e);
            throw new IllegalStateException("小程序码暂时不可用");
          }
        }
        bytes = cached;
      }
    }
    return Base64.getEncoder().encodeToString(bytes);
  }
}
