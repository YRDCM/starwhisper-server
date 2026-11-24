package com.starwhisper.server.config;

import com.starwhisper.server.common.AdminAuthInterceptor;
import com.starwhisper.server.common.AuthInterceptor;
import com.starwhisper.server.common.UsageTrackInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域（CORS）设置 + 拦截器注册
 *
 * 前端 Vue 开发服务器（比如 http://localhost:5173）直连后端接口时，
 * 浏览器会做同源检查，这里放行本机任意端口，方便联调。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final UsageTrackInterceptor usageTrackInterceptor;
  private final AdminAuthInterceptor adminAuthInterceptor;

  public WebConfig(AuthInterceptor authInterceptor,
                   UsageTrackInterceptor usageTrackInterceptor,
                   AdminAuthInterceptor adminAuthInterceptor) {
    this.authInterceptor = authInterceptor;
    this.usageTrackInterceptor = usageTrackInterceptor;
    this.adminAuthInterceptor = adminAuthInterceptor;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")                 // 只放行后端 API 路径
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*") // 本机开发环境
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 常用请求方法
        .allowedHeaders("*")                       // 允许任意请求头（含 X-Admin-Key / X-Dev-Token）
        .maxAge(3600);                             // 预检结果缓存 1 小时，减少 OPTIONS 请求
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 1) 后台鉴权放最前面：/api/admin/** 与运维接口只认 X-Admin-Key，连 token 都不解析
    //    /api/fortune/generate 是批量写库的运维接口，原先完全公开，这里一并纳入后台鉴权
    registry.addInterceptor(adminAuthInterceptor)
        .addPathPatterns("/api/admin/**", "/api/fortune/generate")
        .order(0);

    // 2) 登录拦截：解析 token 挂当前用户；只有 /api/user/**、/api/notify/**、/api/checkin* 强制登录
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .order(1);

    // 3) 使用埋点：在登录拦截之后（需要 CurrentUser），记录关键功能接口（含游客）
    registry.addInterceptor(usageTrackInterceptor)
        .addPathPatterns("/api/**")
        .order(2);
  }
}
