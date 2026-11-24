package com.starwhisper.server.service;

import com.starwhisper.server.entity.FortuneDaily;
import com.starwhisper.server.entity.Sign;

import java.time.LocalDate;

/**
 * 运势数据源接口（策略模式）：
 * 本地生成器和外部 API 都实现这个接口，FortuneService 按需选择，
 * 以后再加别的数据源（比如另一家 API）只要新增一个实现类
 */
public interface FortuneProvider {

  /**
   * 取某星座某一天的运势（只负责"取数/生成"，不落库，由调用方保存）
   *
   * @param sign 星座
   * @param date 运势日期
   * @return 未持久化的 FortuneDaily
   * @throws RuntimeException 取数失败时抛出（调用方负责回退）
   */
  FortuneDaily fetch(Sign sign, LocalDate date);
}
