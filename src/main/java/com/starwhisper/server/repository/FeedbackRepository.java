package com.starwhisper.server.repository;

import com.starwhisper.server.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

/**
 * 用户反馈数据访问层
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

  /** 按 IP 统计某时间之后的提交数（限频用） */
  long countByIpAndCreatedAtAfter(String ip, LocalDateTime since);

  /** 后台查看反馈列表（最新在前） */
  Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
