package com.starwhisper.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务支持（@Scheduled 生效的前提）
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
