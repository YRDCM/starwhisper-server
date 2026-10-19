package com.starwhisper.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 运行期可调参数（后台「参数配置」模块）：
 * 只放运营类开关/限额，密钥类（AppSecret、KIMI_KEY、ADMIN_KEY、数据库密码）绝不进这张表——
 * 密钥仍然走环境变量，改动需要重启是刻意的安全边界。
 */
@Entity
@Table(name = "app_config")
public class AppConfig {

  @Id
  @Column(name = "config_key", length = 128)
  private String configKey;

  @Column(name = "config_value", columnDefinition = "TEXT")
  private String configValue;

  private LocalDateTime updatedAt;

  public AppConfig() {
  }

  public AppConfig(String configKey, String configValue, LocalDateTime updatedAt) {
    this.configKey = configKey;
    this.configValue = configValue;
    this.updatedAt = updatedAt;
  }

  public String getConfigKey() {
    return configKey;
  }

  public void setConfigKey(String configKey) {
    this.configKey = configKey;
  }

  public String getConfigValue() {
    return configValue;
  }

  public void setConfigValue(String configValue) {
    this.configValue = configValue;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
