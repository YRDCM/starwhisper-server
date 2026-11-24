package com.starwhisper.server.repository;

import com.starwhisper.server.entity.Sign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 星座数据访问层
 * 继承 JpaRepository 就自动拥有了增删改查，不用自己写 SQL
 */
public interface SignRepository extends JpaRepository<Sign, Long> {

  // 按英文名查（忽略大小写，前端传 aries / Aries 都行）
  Optional<Sign> findByNameEnIgnoreCase(String nameEn);

  // 按中文名查（如 "天秤座"）
  Optional<Sign> findByName(String name);

  // 按 id 升序取全部，生成运势时保证顺序稳定
  List<Sign> findAllByOrderByIdAsc();
}
