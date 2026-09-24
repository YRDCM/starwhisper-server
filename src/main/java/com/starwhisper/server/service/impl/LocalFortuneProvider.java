package com.starwhisper.server.service.impl;

import com.starwhisper.server.entity.FortuneDaily;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.SignRepository;
import com.starwhisper.server.service.FortuneGenerator;
import com.starwhisper.server.service.FortuneProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 本地运势数据源：包装现有的确定性生成器（FortuneGenerator），
 * 不依赖任何外部服务，是永远的兜底方案
 */
@Component
public class LocalFortuneProvider implements FortuneProvider {

  private final FortuneGenerator fortuneGenerator;
  private final SignRepository signRepository;

  public LocalFortuneProvider(FortuneGenerator fortuneGenerator, SignRepository signRepository) {
    this.fortuneGenerator = fortuneGenerator;
    this.signRepository = signRepository;
  }

  @Override
  public FortuneDaily fetch(Sign sign, LocalDate date) {
    // 速配星座需要从全部星座里挑，所以先把 id 列表查出来（按 id 升序保证稳定）
    List<Long> signIds = signRepository.findAllByOrderByIdAsc()
        .stream().map(Sign::getId).toList();
    FortuneDaily fortune = fortuneGenerator.generate(sign.getId(), date, signIds);
    fortune.setSource("LOCAL");
    return fortune;
  }
}
