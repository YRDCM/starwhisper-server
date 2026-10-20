package com.starwhisper.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * 微信小程序码（wxacode.getUnlimited）：海报分享裂变用。
 * 首次生成后落盘缓存（/opt/starwhisper/mpcode/{scene}.png），同一 scene 只调一次微信。
 * 返回 base64 给小程序端（避免小程序 downloadFile 合法域名的额外配置）。
 */
@Service
public class WechatMpCodeService {

  private static final Logger log = LoggerFactory.getLogger(WechatMpCodeService.class);

  private static final String API = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=";

  private final WechatAccessTokenManager tokenManager;
  private final RestClient restClient;
  private final Path cacheDir;

  public WechatMpCodeService(WechatAccessTokenManager tokenManager,
                             @Value("${wx.mpcode.cache-dir:/opt/starwhisper/mpcode}") String cacheDir) {
    this.tokenManager = tokenManager;
    this.cacheDir = Path.of(cacheDir);
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(15000);
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 取小程序码 PNG 的 base64。scene 限 32 字符内（只保留微信允许的字符集）。
   * 微信报错（未发布/额度等）抛 IllegalStateException，调用方自行降级（海报无码也能出）
   */
  public String mpCodeBase64(String scene) {
    String safeScene = sanitize(scene);
    try {
      Files.createDirectories(cacheDir);
      Path file = cacheDir.resolve((safeScene.isEmpty() ? "fortune" : safeScene) + ".png");
      byte[] png = Files.exists(file) ? Files.readAllBytes(file) : generate(safeScene, file);
      return Base64.getEncoder().encodeToString(png);
    } catch (IOException e) {
      throw new IllegalStateException("小程序码缓存读写失败：" + e.getMessage());
    }
  }

  private byte[] generate(String scene, Path file) throws IOException {
    String token = tokenManager.getAccessToken();
    // check_path=false：page 仅作跳转目标，不校验页面是否存在于已发布版本
    Map<String, Object> body = Map.of(
        "scene", scene,
        "page", "pages/fortune/fortune",
        "width", 200,
        "check_path", false,
        "env_version", "release");
    byte[] resp = restClient.post()
        .uri(API + token)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(byte[].class);
    if (resp == null || resp.length == 0) {
      throw new IllegalStateException("微信返回空响应");
    }
    // 出错时微信返回 JSON 而不是 PNG
    if (resp[0] == '{') {
      String error = new String(resp, StandardCharsets.UTF_8);
      log.warn("小程序码生成失败：scene={}, 微信返回={}", scene, error);
      throw new IllegalStateException("微信接口报错：" + error);
    }
    Files.write(file, resp);
    log.info("小程序码已生成并缓存：scene={}, file={}", scene, file);
    return resp;
  }

  /** scene 清洗：微信允许 数字/英文/!#$&'()*+,/:;=?@-._~，且 ≤32 字符 */
  public static String sanitize(String scene) {
    if (scene == null) return "";
    String cleaned = scene.replaceAll("[^0-9A-Za-z!#$&'()*+,/:;=?@\\-._~]", "");
    return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32);
  }
}
