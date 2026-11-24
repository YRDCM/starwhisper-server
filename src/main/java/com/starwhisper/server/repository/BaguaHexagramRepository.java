package com.starwhisper.server.repository;

import com.starwhisper.server.entity.BaguaHexagram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 六十四卦数据访问层
 */
public interface BaguaHexagramRepository extends JpaRepository<BaguaHexagram, Long> {

  // 按卦序查（1-64 唯一）
  Optional<BaguaHexagram> findBySeqNumber(Integer seqNumber);

  // 按六爻组合查：起卦时由爻位反查卦（自下而上，1=阳 0=阴）
  Optional<BaguaHexagram> findByLines(String lines);
}
