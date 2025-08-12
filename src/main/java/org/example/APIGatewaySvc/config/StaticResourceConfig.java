package org.example.APIGatewaySvc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.time.Duration;

/**
 * 정적 리소스 설정
 * /public/** 경로에 대한 캐시 헤더 설정 및 리소스 매핑
 */
@Configuration
public class StaticResourceConfig implements WebFluxConfigurer {

    /**
     * 정적 리소스 핸들러 설정
     * - /public/** 경로를 classpath:/static/로 매핑
     * - 캐시 헤더 설정 (개발: 캐시 없음, 운영: 1시간)
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /public/** 경로에 대한 정적 리소스 매핑
        registry.addResourceHandler("/public/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1))
                    .mustRevalidate()
                    .cachePublic());
        
        // 개발 모드에서는 캐시 비활성화
        registry.addResourceHandler("/public/auth-test.html")
                .addResourceLocations("classpath:/static/auth-test.html")
                .setCacheControl(CacheControl.noCache());
    }
}