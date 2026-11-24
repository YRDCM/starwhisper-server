package com.starwhisper.server.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 会话令牌工具。
 *
 * 设计要点：**数据库里只存令牌的 SHA-256 摘要，不存明文**。
 * 这样即使数据库被拖走，攻击者也拿不到可用的 token（摘要无法反推算）。
 * 代价是：明文只在发出去的那一刻返回给客户端，服务端之后无法还原。
 *
 * 令牌用 SecureRandom 生成 32 字节 → 64 位十六进制，
 * 与 user_session.token 的 varchar(64) 刚好吻合，熵（256bit）远高于原来的 UUID（122bit）。
 */
public final class TokenHasher {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private TokenHasher() {
  }

  /** 生成一个新的明文令牌（32 字节随机 → 64 位 hex） */
  public static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return toHex(bytes);
  }

  /** 计算令牌摘要（SHA-256 → 64 位 hex），落库与查库都用它 */
  public static String hash(String rawToken) {
    if (rawToken == null) {
      throw new IllegalArgumentException("令牌不能为 null");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return toHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JDK 必备算法，走不到这里
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /** 常量时间比较，避免通过响应时间差逐字节猜密钥 */
  public static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8),
        b.getBytes(StandardCharsets.UTF_8));
  }

  /** 日志脱敏：只留前 6 位 */
  public static String mask(String token) {
    if (token == null || token.length() <= 6) {
      return "***";
    }
    return token.substring(0, 6) + "...";
  }

  private static String toHex(byte[] bytes) {
    char[] out = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      out[i * 2] = HEX[v >>> 4];
      out[i * 2 + 1] = HEX[v & 0x0F];
    }
    return new String(out);
  }
}
