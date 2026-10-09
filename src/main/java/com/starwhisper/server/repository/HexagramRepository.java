package com.starwhisper.server.repository;

import com.starwhisper.server.entity.Hexagram;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 六十四卦（每日一卦）数据访问层
 */
public interface HexagramRepository extends JpaRepository<Hexagram, Integer> {
}
