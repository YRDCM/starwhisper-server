package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.entity.CheckIn;
import com.starwhisper.server.entity.UserHistory;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.CheckInRepository;
import com.starwhisper.server.repository.UserHistoryRepository;
import com.starwhisper.server.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 后台用户管理：分页组装 / 连续打卡口径 / 详情历史兜底
 */
class AdminUserServiceTests {

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final CheckInRepository checkins = mock(CheckInRepository.class);
  private final UserHistoryRepository histories = mock(UserHistoryRepository.class);
  private final UserSessionRepository sessions = mock(UserSessionRepository.class);
  private final CheckInService checkInService = mock(CheckInService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private AdminUserService service() {
    return new AdminUserService(users, checkins, histories, sessions, checkInService, objectMapper);
  }

  private AppUser user(Long id, String nickname) {
    AppUser u = new AppUser();
    u.setId(id);
    u.setNickname(nickname);
    u.setAvatarUrl("https://cdn.example.com/a" + id + ".png");
    u.setSignNameEn("aries");
    u.setCreatedAt(LocalDateTime.now().minusDays(30));
    return u;
  }

  @Test void pageAssemblesCheckinsStreakAndLastActive() {
    var pageUsers = List.of(user(2L, "星野"), user(1L, "流萤"));
    when(users.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(pageUsers, Pageable.unpaged(), 12));

    LocalDate today = LocalDate.now();
    when(checkins.findByUserIdInOrderByCheckDateDesc(List.of(2L, 1L))).thenReturn(List.of(
        new CheckIn(2L, today, LocalDateTime.now()),
        new CheckIn(2L, today.minusDays(1), LocalDateTime.now()),
        new CheckIn(1L, today.minusDays(2), LocalDateTime.now())));

    LocalDateTime lastSeen = LocalDateTime.now().minusHours(3);
    when(sessions.findLastActiveAtByUserIdIn(List.of(2L, 1L)))
        .thenReturn(List.<Object[]>of(new Object[]{2L, lastSeen}));

    Map<String, Object> data = service().users(0, 20, null);

    assertEquals(12L, data.get("total"));
    assertEquals(0, data.get("page"));
    assertEquals(20, data.get("size"));

    @SuppressWarnings("unchecked")
    var list = (List<Map<String, Object>>) data.get("list");
    assertEquals(2, list.size());

    var first = list.get(0);
    assertEquals(2L, first.get("id"));
    assertEquals("星野", first.get("nickname"));
    assertEquals("aries", first.get("signNameEn"));
    assertEquals(2, first.get("totalCheckins"));
    assertEquals(2, first.get("currentStreak")); // 今天+昨天连打
    assertEquals(lastSeen, first.get("lastActiveAt"));

    var second = list.get(1);
    assertEquals(1, second.get("totalCheckins"));
    assertEquals(0, second.get("currentStreak")); // 昨天断了 → 0
    assertNull(second.get("lastActiveAt"));       // 从没登录过（PC 后台场景）
  }

  @Test void keywordTrimsAndDelegatesToNicknameSearch() {
    when(users.findByNicknameContainingIgnoreCase(eq("星"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(user(3L, "星尘")), Pageable.unpaged(), 1));
    Map<String, Object> data = service().users(0, 20, "  星  ");
    assertEquals(1L, data.get("total"));
    verify(users).findByNicknameContainingIgnoreCase(eq("星"), any(Pageable.class));
    verify(users, never()).findAll(any(Pageable.class));
  }

  @Test void pageParamsAreSanitized() {
    when(users.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
    Map<String, Object> data = service().users(-3, 9999, "");
    assertEquals(0, data.get("page"));                             // 负页钳到 0
    assertEquals(AdminUserService.MAX_PAGE_SIZE, data.get("size")); // 超大 size 钳到上限
    // 空 keyword 视为未搜索
    verify(users).findAll(any(Pageable.class));
    verify(users, never()).findByNicknameContainingIgnoreCase(any(), any());
  }

  @Test void streakMatchesCheckInServiceSemantics() {
    LocalDate today = LocalDate.now();
    assertEquals(0, AdminUserService.currentStreak(List.of()));
    // 今天打了：从今天往前数
    assertEquals(2, AdminUserService.currentStreak(List.of(today, today.minusDays(1))));
    // 今天没打但昨天打了：从昨天往前数（今天还有机会补）
    assertEquals(1, AdminUserService.currentStreak(List.of(today.minusDays(1))));
    // 昨天也断：0
    assertEquals(0, AdminUserService.currentStreak(List.of(today.minusDays(2), today.minusDays(3))));
  }

  @Test void detailCombinesBaseSummaryAndRecentHistories() {
    AppUser u = user(7L, "北斗");
    when(users.findById(7L)).thenReturn(Optional.of(u));
    when(checkInService.summaryByUserId(7L)).thenReturn(Map.of("currentStreak", 5, "totalDays", 9));

    UserHistory withTitle = new UserHistory();
    withTitle.setType("TAROT");
    withTitle.setTitle("单牌指引 · 愚者 正位");
    withTitle.setCreatedAt(LocalDateTime.now());

    UserHistory noTitle = new UserHistory();
    noTitle.setType("MATCH");
    noTitle.setDetail("{\"star1\":\"aries\",\"star2\":\"leo\",\"score\":92}");
    noTitle.setCreatedAt(LocalDateTime.now().minusDays(1));

    UserHistory dirtyDetail = new UserHistory();
    dirtyDetail.setType("hexagram");
    dirtyDetail.setDetail("{broken json 这是一段没有标题的原始明细，足够长用来验证截断逻辑是否正常工作……");
    dirtyDetail.setCreatedAt(LocalDateTime.now().minusDays(2));

    when(histories.findTop20ByUserIdOrderByCreatedAtDesc(7L))
        .thenReturn(List.of(withTitle, noTitle, dirtyDetail));

    Map<String, Object> data = service().userDetail(7L);

    @SuppressWarnings("unchecked")
    var base = (Map<String, Object>) data.get("user");
    assertEquals("北斗", base.get("nickname"));
    assertEquals(Map.of("currentStreak", 5, "totalDays", 9), data.get("checkin"));

    @SuppressWarnings("unchecked")
    var items = (List<Map<String, Object>>) data.get("histories");
    assertEquals(3, items.size());
    assertEquals("单牌指引 · 愚者 正位", items.get(0).get("title")); // 有 title 直接用
    assertEquals("aries", items.get(1).get("title"));              // 从 detail JSON 提取兜底
    assertTrue(items.get(2).get("title").toString().endsWith("…")); // 脏 JSON 裸截断
    assertTrue(items.get(2).get("title").toString().length() <= 61);
  }

  @Test void detailThrowsForMissingUser() {
    when(users.findById(99L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service().userDetail(99L));
  }

  private UserHistory hist(Long userId, String type, String title, LocalDateTime at) {
    UserHistory h = new UserHistory();
    h.setUserId(userId);
    h.setType(type);
    h.setTitle(title);
    h.setCreatedAt(at);
    return h;
  }

  @Test void dailyUsageGroupsModulesAndTimeline() {
    LocalDate day = LocalDate.of(2026, 10, 19);
    when(users.existsById(9L)).thenReturn(true);
    when(histories.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
        eq(9L), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of(
            hist(9L, "TAROT", "单牌指引", day.atTime(9, 0)),
            hist(9L, "TAROT", "三牌阵", day.atTime(9, 5)),
            hist(9L, "MATCH", "白羊×金牛", day.atTime(10, 0)),
            hist(9L, "hexagram", "每日一卦", day.atTime(11, 0))));
    when(sessions.countByUserIdAndCreatedAtBetween(eq(9L), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(2L);
    when(checkins.countByUserIdAndCheckDate(9L, day)).thenReturn(1L);

    Map<String, Object> data = service().dailyUsage(9L, day);

    assertEquals("2026-10-19", data.get("date"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
    Map<String, Map<String, Object>> byKey = modules.stream().collect(java.util.stream.Collectors.toMap(m -> (String) m.get("key"), m -> m));
    assertEquals(2L, byKey.get("login").get("count"));
    assertEquals(1L, byKey.get("checkin").get("count"));
    assertEquals(2L, byKey.get("tarot").get("count"));
    assertEquals(1L, byKey.get("match").get("count"));
    assertEquals(1L, byKey.get("hexagram").get("count"));
    assertEquals(0L, byKey.get("bagua").get("count"));
    assertNull(byKey.get("fortune").get("count")); // 未埋点必须是 null 而不是 0
    assertNull(byKey.get("ai").get("count"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("events");
    assertEquals(4, events.size());
    assertEquals("09:00", events.get(0).get("time")); // 时间升序
  }

  @Test void dailyUsageDefaultsToTodayAndRejectsMissingUser() {
    when(users.existsById(9L)).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> service().dailyUsage(9L, null));

    when(users.existsById(9L)).thenReturn(true);
    Map<String, Object> data = service().dailyUsage(9L, null);
    assertEquals(LocalDate.now().toString(), data.get("date"));
  }
}
