package com.starwhisper.server.config;

import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.SignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 数据初始化器：项目启动时自动往数据库灌入 12 星座数据
 * 幂等：表里已有数据就跳过，重启不会重复插入
 */
@Configuration
public class DataInitializer {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  @Bean
  public CommandLineRunner initSigns(SignRepository signRepository) {
    return args -> {
      // 表里已经有数据就不重复初始化
      if (signRepository.count() > 0) {
        log.info("星座数据已存在，跳过初始化（当前 {} 条）", signRepository.count());
        return;
      }

      // 12 星座数据（第 3 课从内存搬到数据库）
      List<Sign> signs = List.of(
          new Sign(null, "白羊座", "aries",       "♈", "火象", "03.21-04.19", 3, 21, 4, 19),
          new Sign(null, "金牛座", "taurus",      "♉", "土象", "04.20-05.20", 4, 20, 5, 20),
          new Sign(null, "双子座", "gemini",      "♊", "风象", "05.21-06.21", 5, 21, 6, 21),
          new Sign(null, "巨蟹座", "cancer",      "♋", "水象", "06.22-07.22", 6, 22, 7, 22),
          new Sign(null, "狮子座", "leo",         "♌", "火象", "07.23-08.22", 7, 23, 8, 22),
          new Sign(null, "处女座", "virgo",       "♍", "土象", "08.23-09.22", 8, 23, 9, 22),
          new Sign(null, "天秤座", "libra",       "♎", "风象", "09.23-10.23", 9, 23, 10, 23),
          new Sign(null, "天蝎座", "scorpio",     "♏", "水象", "10.24-11.22", 10, 24, 11, 22),
          new Sign(null, "射手座", "sagittarius", "♐", "火象", "11.23-12.21", 11, 23, 12, 21),
          new Sign(null, "摩羯座", "capricorn",   "♑", "土象", "12.22-01.19", 12, 22, 1, 19),
          new Sign(null, "水瓶座", "aquarius",    "♒", "风象", "01.20-02.18", 1, 20, 2, 18),
          new Sign(null, "双鱼座", "pisces",      "♓", "水象", "02.19-03.20", 2, 19, 3, 20)
      );

      signRepository.saveAll(signs);
      log.info("12 星座数据初始化完成");
    };
  }
}
