package com.starwhisper.server.service;

import com.starwhisper.server.entity.Sign;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 星座业务层
 */
@Service
public class SignService {

  // 12 星座数据（第 3 课会搬进数据库）
  private final List<Sign> signs = new ArrayList<>();

  public SignService() {
    signs.add(new Sign(1L,  "白羊座", "aries",       "♈", "火象", "03.21-04.19", 3, 21, 4, 19));
    signs.add(new Sign(2L,  "金牛座", "taurus",      "♉", "土象", "04.20-05.20", 4, 20, 5, 20));
    signs.add(new Sign(3L,  "双子座", "gemini",      "♊", "风象", "05.21-06.21", 5, 21, 6, 21));
    signs.add(new Sign(4L,  "巨蟹座", "cancer",      "♋", "水象", "06.22-07.22", 6, 22, 7, 22));
    signs.add(new Sign(5L,  "狮子座", "leo",         "♌", "火象", "07.23-08.22", 7, 23, 8, 22));
    signs.add(new Sign(6L,  "处女座", "virgo",       "♍", "土象", "08.23-09.22", 8, 23, 9, 22));
    signs.add(new Sign(7L,  "天秤座", "libra",       "♎", "风象", "09.23-10.23", 9, 23, 10, 23));
    signs.add(new Sign(8L,  "天蝎座", "scorpio",     "♏", "水象", "10.24-11.22", 10, 24, 11, 22));
    signs.add(new Sign(9L,  "射手座", "sagittarius", "♐", "火象", "11.23-12.21", 11, 23, 12, 21));
    signs.add(new Sign(10L, "摩羯座", "capricorn",   "♑", "土象", "12.22-01.19", 12, 22, 1, 19));
    signs.add(new Sign(11L, "水瓶座", "aquarius",    "♒", "风象", "01.20-02.18", 1, 20, 2, 18));
    signs.add(new Sign(12L, "双鱼座", "pisces",      "♓", "水象", "02.19-03.20", 2, 19, 3, 20));
  }

  /**
   * 查询全部星座
   */
  public List<Sign> list() {
    return signs;
  }
}