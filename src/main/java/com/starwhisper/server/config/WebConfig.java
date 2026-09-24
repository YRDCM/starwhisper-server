package com.starwhisper.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域（CORS）设置
 * 前端 Vue 开发服务器（比如 http://localhost:5173）直连后端接口时，
 * 浏览器会做同源检查，这里放行本机任意端口，方便联调
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")                 // 只放行后端 API 路径
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*") // 本机开发环境
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 常用请求方法
        .allowedHeaders("*")                       // 允许任意请求头
        .maxAge(3600);                             // 预检结果缓存 1 小时，减少 OPTIONS 请求
  }
}
