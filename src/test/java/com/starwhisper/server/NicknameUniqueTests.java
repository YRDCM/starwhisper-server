package com.starwhisper.server.service;

import com.starwhisper.server.entity.AppUser;
import com.starwhisper.server.repository.AppUserRepository;
import com.starwhisper.server.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 昵称唯一性：默认/指定昵称撞名时自动加 ·xxxx 后缀
 */
class NicknameUniqueTests {

  private AuthService newService(AppUserRepository repo) {
    // 第 6 个参数是 dev-login 的校验令牌；测试只用 uniqueNickname，与它无关
    return new AuthService(repo, mock(UserSessionRepository.class), "", "", true, "test-token");
  }

  @Test
  void 昵称没被撞就原样用() {
    AppUserRepository repo = mock(AppUserRepository.class);
    when(repo.findByNickname(anyString())).thenReturn(Optional.empty());

    assertEquals("小明", newService(repo).uniqueNickname("小明"));
  }

  @Test
  void 昵称撞名自动加后缀() {
    AppUserRepository repo = mock(AppUserRepository.class);
    // "星语旅人" 被占用，带后缀的候选没占用
    when(repo.findByNickname("星语旅人")).thenReturn(Optional.of(new AppUser()));
    when(repo.findByNickname(argThat((String s) -> s.startsWith("星语旅人·")))).thenReturn(Optional.empty());

    String result = newService(repo).uniqueNickname(null); // 没传昵称 → 默认打底
    assertTrue(result.matches("星语旅人·[0-9a-f]{4}"), "应加 4 位十六进制后缀，实际：" + result);
  }

  @Test
  void 传入昵称撞名也加后缀() {
    AppUserRepository repo = mock(AppUserRepository.class);
    when(repo.findByNickname("小明")).thenReturn(Optional.of(new AppUser()));
    when(repo.findByNickname(argThat((String s) -> s.startsWith("小明·")))).thenReturn(Optional.empty());

    assertTrue(newService(repo).uniqueNickname("小明").startsWith("小明·"));
  }

  @Test
  void 空白昵称按默认打底处理() {
    AppUserRepository repo = mock(AppUserRepository.class);
    when(repo.findByNickname(anyString())).thenReturn(Optional.empty());

    assertEquals("星语旅人", newService(repo).uniqueNickname("  "));
  }
}
