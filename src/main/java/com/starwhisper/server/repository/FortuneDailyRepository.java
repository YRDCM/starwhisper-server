package com.starwhisper.server.repository;

import com.starwhisper.server.entity.FortuneDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 每日运势数据访问层
 */
public interface FortuneDailyRepository extends JpaRepository<FortuneDaily, Long> {

  // 查某个星座某一天的运势（唯一约束保证最多一条）
  Optional<FortuneDaily> findBySignIdAndFortuneDate(Long signId, LocalDate fortuneDate);

  // 查某一天全部星座的运势
  List<FortuneDaily> findByFortuneDate(LocalDate fortuneDate);

  // 查某个星座一段日期范围内的运势，按日期升序（周报用）
  List<FortuneDaily> findBySignIdAndFortuneDateBetweenOrderByFortuneDateAsc(Long signId, LocalDate start, LocalDate end);
}
