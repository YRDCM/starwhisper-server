package com.starwhisper.server.service;

import com.starwhisper.server.dto.DrawResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Single-instance bounded cache; attempts (including provider failures) consume quota. */
@Service
public class TarotAiService {
  private final KimiTarotClient client;
  private final AiDailyQuota quota;
  private final Map<String, Session> sessions = new LinkedHashMap<>();
  private int active;
  private static final long TTL = Duration.ofHours(2).toMillis();
  private static class Session {
    final DrawResultVO draw;
    final long created = System.currentTimeMillis();
    boolean running;
    String question;
    KimiTarotClient.Reading reading;
    Session(DrawResultVO draw) { this.draw = draw; }
  }

  @Autowired
  public TarotAiService(KimiTarotClient client, AiDailyQuota quota) {
    this.client = client;
    this.quota = quota;
  }

  // 测试用便捷构造：自建独立额度池
  public TarotAiService(KimiTarotClient client, int perIpLimit, int globalLimit) {
    this(client, new AiDailyQuota(perIpLimit, globalLimit));
  }

  public synchronized String register(DrawResultVO draw) {
    if (!client.enabled()) return null;
    sessions.entrySet().removeIf(e -> !e.getValue().running && System.currentTimeMillis() - e.getValue().created > TTL);
    if (sessions.size() >= 2000) {
      var oldest = sessions.entrySet().stream().filter(e -> !e.getValue().running).findFirst();
      if (oldest.isEmpty()) return null;
      sessions.remove(oldest.get().getKey());
    }
    String id = UUID.randomUUID().toString();
    sessions.put(id, new Session(draw));
    return id;
  }

  /** 兼容旧签名：无登录身份（按游客额度计） */
  public KimiTarotClient.Reading read(String drawId, String question, String ip) {
    return read(drawId, question, ip, null);
  }

  /**
   * @param userId 登录用户 id；为 null 表示游客。
   *               登录用户走独立额度（比游客多），这是登录的实际权益之一。
   */
  public KimiTarotClient.Reading read(String drawId, String question, String ip, Long userId) {
    if (drawId == null || drawId.length() > 64 || question == null || question.length() > 300)
      throw new IllegalArgumentException("请使用当前牌阵，问题不超过300字");
    final Session session;
    synchronized (this) {
      if (!client.enabled()) throw new IllegalStateException("AI 解牌尚未启用，请先阅读基础牌义");
      session = sessions.get(drawId);
      if (session == null || System.currentTimeMillis() - session.created > TTL)
        throw new IllegalArgumentException("本次牌阵已过期，请重新抽牌后解读");
      if (session.reading != null) {
        if (!Objects.equals(session.question, question.strip())) throw new IllegalArgumentException("本组牌已生成解读，请恢复原问题后重试，或重新抽牌");
        return session.reading;
      }
      if (session.running) throw new IllegalStateException("这组牌正在解读，请稍候再查看");
      if (active >= 2) throw new IllegalStateException("AI 正在忙，请稍后再试");
      quota.consume(ip, userId);
      active++;
      session.running = true;
      session.question = question.strip();
    }
    try {
      var result = client.read(session.draw, question.strip());
      synchronized (this) { session.reading = result; }
      return result;
    } finally {
      synchronized (this) { active--; session.running = false; }
    }
  }
}
