package com.starwhisper.server.service;

import com.starwhisper.server.entity.Sign;
import com.starwhisper.server.repository.SignRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 星座业务层
 * 第 3 课：数据从内存搬进数据库，通过 SignRepository 查询
 */
@Service
public class SignService {

  private final SignRepository signRepository;

  // 构造注入：Spring 启动时自动把 SignRepository 实例传进来
  public SignService(SignRepository signRepository) {
    this.signRepository = signRepository;
  }

  /**
   * 查询全部星座（从数据库读取）
   */
  public List<Sign> list() {
    return signRepository.findAll();
  }
}
