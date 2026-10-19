package com.starwhisper.server.repository;

import com.starwhisper.server.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 运行期参数数据访问层
 */
public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
}
