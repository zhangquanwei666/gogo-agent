package com.quanwei.gogo.agent.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域配置。
 *
 * <p>用 Filter 而不是 WebMvcConfigurer#addCorsMappings，是因为 Filter 的执行时机在
 * Spring MVC 的拦截器之前。浏览器的预检请求（OPTIONS）不带业务 token，
 * 如果先过 SaInterceptor 会被判成未登录直接 401，预检失败后真实请求根本发不出来。
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();

        // allowCredentials 为 true 时不能用 addAllowedOrigin("*")，规范禁止，
        // 必须走 OriginPattern。生产环境建议换成明确的域名白名单
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        // 把 token 头暴露给前端 JS，否则跨域下读不到自定义响应头
        config.addExposedHeader("Authorization");
        // 预检结果缓存 1 小时，减少 OPTIONS 请求
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        // 排在过滤器链最前面，确保任何鉴权逻辑之前就把跨域头写好
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
