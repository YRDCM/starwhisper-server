package com.starwhisper.server.service;

import com.starwhisper.server.dto.WeeklyFortuneVO;
import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.SignRepository;
import com.starwhisper.server.service.impl.ShowapiFortuneProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本周运势（/api/fortune/weekly）单元测试：
 * 不碰网络、不碰数据库，全部用 mock 仓储/缓存 + 真实 FortuneGenerator
 */
class FortuneWeeklyTests {

  private static Sign sign(long id, String name, String nameEn) {
    return new Sign(id, name, nameEn, "♌", "火象", "07.23-08.22", 7, 23, 8, 22);
  }

  private static SignRepository twelveSigns() {
    SignRepository repo = mock(SignRepository.class);
    List<Sign> signs = List.of(
        sign(1, "白羊座", "aries"), sign(2, "金牛座", "taurus"), sign(3, "双子座", "gemini"),
        sign(4, "巨蟹座", "cancer"), sign(5, "狮子座", "leo"), sign(6, "处女座", "virgo"),
        sign(7, "天秤座", "libra"), sign(8, "天蝎座", "scorpio"), sign(9, "射手座", "sagittarius"),
        sign(10, "摩羯座", "capricorn"), sign(11, "水瓶座", "aquarius"), sign(12, "双鱼座", "pisces"));
    when(repo.findAllByOrderByIdAsc()).thenReturn(signs);
    return repo;
  }

  private static ShowapiFortuneProvider provider(boolean mock, HoroscopeCacheService cache) {
    return new ShowapiFortuneProvider(
        twelveSigns(), new FortuneGenerator(), cache, new ObjectMapper(), "dummy-key", mock);
  }

  @Test
  void weekCacheKeyUsesIsoMonday() {
    // 2026-10-12 本身就是周一
    assertEquals("leo:week:2026-10-12",
        ShowapiFortuneProvider.weekCacheKey("leo", LocalDate.of(2026, 10, 12)));
    // 周日 2026-10-18 仍归到同一个 ISO 周（周一 10-12），一周内 key 不变
    assertEquals("leo:week:2026-10-12",
        ShowapiFortuneProvider.weekCacheKey("leo", LocalDate.of(2026, 10, 18)));
    // 下周一换 key，天然不串周
    assertEquals("leo:week:2026-10-19",
        ShowapiFortuneProvider.weekCacheKey("leo", LocalDate.of(2026, 10, 19)));
    // 星座名大小写不敏感
    assertEquals("leo:week:2026-10-12",
        ShowapiFortuneProvider.weekCacheKey("LEO", LocalDate.of(2026, 10, 12)));
  }

  @Test
  void extractWeekRejectsBodyWithoutWeek() {
    // 不带 needWeek=1 时响应没有 week 键 —— 必须抛错，不能编造
    assertThrows(IllegalStateException.class,
        () -> ShowapiFortuneProvider.extractWeek(Map.of("ret_code", "0")));
  }

  @Test
  void extractWeekAcceptsMapAndListShape() {
    Map<String, Object> week = Map.of("summary_star", 4);
    assertEquals(4, ShowapiFortuneProvider.extractWeek(Map.of("week", week)).get("summary_star"));
    // 防御：万一上游改成数组形态（day 字段就有过文档与实际不符的前科）
    assertEquals(4, ShowapiFortuneProvider.extractWeek(Map.of("week", List.of(week))).get("summary_star"));
  }

  @Test
  void weeklyMappingParsesStringAndNumberScores() {
    ShowapiFortuneProvider p = provider(false, mock(HoroscopeCacheService.class));
    Map<String, Object> week = new HashMap<>();
    week.put("summary_star", "4");   // 字符串形态（日运实测全是字符串）
    week.put("love_star", 5);        // 数字形态（周运实测出现）
    week.put("work_star", "abc");    // 非法值 → null，不抛异常
    // money_star 缺字段 → null
    week.put("lucky_num", "71");
    week.put("lucky_day", "星期五");
    week.put("lucky_color", "米白色");
    week.put("grxz", "巨蟹座");
    week.put("xrxz", "射手座");
    week.put("week_notice", "情绪敏感需理性沟通");
    week.put("time", "20261011-20261018");
    week.put("general_txt", "本周整体运势平稳向好。");
    // love_txt / work_txt / money_txt 缺失 → null（前端按可空隐藏）

    WeeklyFortuneVO vo = p.mapToWeeklyVO(week, sign(5, "狮子座", "leo"));

    assertEquals(4, vo.getOverallScore());
    assertEquals(5, vo.getLoveScore());
    assertNull(vo.getCareerScore());
    assertNull(vo.getWealthScore());
    assertEquals(71, vo.getLuckyNumber());
    assertEquals("星期五", vo.getLuckyDay());
    assertEquals("巨蟹座", vo.getPairSignName());
    assertEquals("射手座", vo.getNemesisSignName());
    assertEquals("20261011-20261018", vo.getTimeRange()); // 原样透传
    assertEquals("本周整体运势平稳向好。", vo.getGeneralTxt());
    assertNull(vo.getLoveTxt());
    assertEquals("SHOWAPI", vo.getSource());
  }

  @Test
  void mockWeeklyIsDeterministicAndHonestlyTagged() {
    ShowapiFortuneProvider p = provider(true, mock(HoroscopeCacheService.class));
    Sign leo = sign(5, "狮子座", "leo");

    WeeklyFortuneVO a = p.fetchWeekly(leo);
    WeeklyFortuneVO b = p.fetchWeekly(leo);

    // 确定性：同一星座同一周两次调用结果一致
    assertEquals(a.getOverallScore(), b.getOverallScore());
    assertEquals(a.getLuckyColor(), b.getLuckyColor());
    assertEquals(a.getPairSignName(), b.getPairSignName());
    assertEquals(a.getTimeRange(), b.getTimeRange());

    // 数据诚实：mock 文案必须带【模拟数据】前缀
    assertTrue(a.getGeneralTxt().startsWith("【模拟数据】"));
    assertTrue(a.getWeekNotice().startsWith("【模拟数据】"));
    // 贵人和小人不能是自己、也不能彼此相同
    assertNotEquals("狮子座", a.getPairSignName());
    assertNotEquals("狮子座", a.getNemesisSignName());
    assertNotEquals(a.getPairSignName(), a.getNemesisSignName());
    // timeRange 是周一到周日的 8 位日期区间
    assertTrue(a.getTimeRange().matches("\\d{8}-\\d{8}"));
  }

  @Test
  void weeklyCacheHitAvoidsHttpCall() {
    // 缓存命中时直接解析返回，不发任何 HTTP（provider 里的 RestClient 连的是真实域名，
    // 若走了网络单测会超时失败 —— 能跑通即证明走了缓存）
    HoroscopeCacheService cache = mock(HoroscopeCacheService.class);
    String json = "{\"summary_star\":3,\"lucky_day\":\"星期三\",\"time\":\"20261012-20261018\","
        + "\"week_notice\":\"多喝水\",\"general_txt\":\"平稳的一周。\"}";
    when(cache.getByKey("leo:week:" + LocalDate.now().with(java.time.DayOfWeek.MONDAY)))
        .thenReturn(Optional.of(json));

    ShowapiFortuneProvider p = provider(false, cache);
    WeeklyFortuneVO vo = p.fetchWeekly(sign(5, "狮子座", "leo"));

    assertEquals(3, vo.getOverallScore());
    assertEquals("星期三", vo.getLuckyDay());
    assertEquals("平稳的一周。", vo.getGeneralTxt());
    assertEquals("SHOWAPI", vo.getSource());
  }

  @Test
  void serviceRejectsWeeklyWhenProviderInactive() {
    // 未配 appKey 且非 mock：周运无本地兜底，直接抛错给前端错误态
    ShowapiFortuneProvider inactive = new ShowapiFortuneProvider(
        twelveSigns(), new FortuneGenerator(), mock(HoroscopeCacheService.class),
        new ObjectMapper(), "", false);

    SignRepository signRepo = twelveSigns();
    when(signRepo.findByNameEnIgnoreCase("leo")).thenReturn(Optional.of(sign(5, "狮子座", "leo")));

    FortuneService service = new FortuneService(
        mock(com.starwhisper.server.repository.FortuneDailyRepository.class),
        signRepo,
        mock(com.starwhisper.server.service.impl.LocalFortuneProvider.class),
        inactive);

    assertThrows(IllegalStateException.class, () -> service.getWeeklyFortune("leo"));
  }
}
