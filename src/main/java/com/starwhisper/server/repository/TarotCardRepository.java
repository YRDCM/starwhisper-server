package com.starwhisper.server.repository;

import com.starwhisper.server.entity.TarotCard;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 塔罗牌数据访问层
 */
public interface TarotCardRepository extends JpaRepository<TarotCard, Long> {

}
