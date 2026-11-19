package com.starwhisper.server.repository;

import com.starwhisper.server.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  // 按微信 openid 查用户（登录时用）
  Optional<AppUser> findByOpenid(String openid);

  // 昵称唯一性校验（重名检查用；昵称有唯一索引兜底）
  Optional<AppUser> findByNickname(String nickname);

  // 统计：某时刻之后注册的用户数 / 用户列表（后台新增用户统计用）
  long countByCreatedAtGreaterThanEqual(LocalDateTime since);

  // 统计：某天注册的用户数（运营日报用）
  long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

  // 某天注册的用户 id 列表（留存计算用：注册日 → 后续活跃比对）
  @Query("select u.id from AppUser u where u.createdAt between :start and :end")
  List<Long> findIdsByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  List<AppUser> findByCreatedAtGreaterThanEqual(LocalDateTime since);

  /**
   * 后台用户管理：昵称模糊 + 可选「今日已打卡」筛选。
   *
   * 为什么用 EXISTS 子查询而不是 join：筛选必须发生在**分页之前**（先取本页再在内存里过滤，
   * 每页条数和总数都会算错）。EXISTS 不产生行膨胀，Spring Data 生成的 count 查询也不会被带偏。
   *
   * 参数用 hasKeyword 而不是判断 keyword 是否为 null：JPQL 里对可能为 null 的参数做
   * `:kw is null` 判断，在不同版本上容易撞到参数类型推断问题，用布尔开关更稳。
   */
  @Query("""
      select u from AppUser u
      where (:hasKeyword = false or lower(u.nickname) like lower(:pattern))
        and (:onlyTodayCheckin = false or exists (
              select 1 from CheckIn c where c.userId = u.id and c.checkDate = :today))
      """)
  Page<AppUser> search(@Param("hasKeyword") boolean hasKeyword,
                       @Param("pattern") String pattern,
                       @Param("onlyTodayCheckin") boolean onlyTodayCheckin,
                       @Param("today") LocalDate today,
                       Pageable pageable);
}
