package org.example.APIGatewaySvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;

/**
 * Spring Cloud Gateway CORS 설정 클래스
 * 
 * Auth0 인증과 호환되는 CORS 정책을 설정합니다.
 * 프론트엔드 애플리케이션에서 API Gateway로의 크로스 오리진 요청을 허용하며,
 * 인증 쿠키 및 JWT 토큰 전송을 지원합니다.
 * 
 * 주요 기능:
 * - 환경별 허용 오리진 설정 (개발/프로덕션)
 * - Auth0 인증 쿠키 지원을 위한 credentials 허용
 * - Preflight 요청 최적화
 * - 보안 헤더 설정
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String[] allowedMethods;

    @Value("${app.cors.max-age:3600}")
    private Long maxAge;

    /**
     * CORS 웹 필터 설정
     * Spring Cloud Gateway의 Global CORS 설정과 함께 동작하여
     * 더 세밀한 CORS 제어를 제공합니다.
     * 
     * @return CorsWebFilter CORS 웹 필터
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // 허용할 오리진 설정 (환경변수로 제어)
        corsConfig.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));
        
        // 허용할 HTTP 메서드
        corsConfig.setAllowedMethods(Arrays.asList(allowedMethods));
        
        // 허용할 헤더 (모든 헤더 허용)
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        
        // Auth0 인증 쿠키 전송을 위해 credentials 허용
        corsConfig.setAllowCredentials(true);
        
        // 노출할 헤더 설정 (클라이언트에서 읽을 수 있는 헤더)
        corsConfig.setExposedHeaders(Arrays.asList(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            "X-Requested-With",
            "X-Gateway-Response-Time",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Retry-After-Seconds"
        ));
        
        // Preflight 요청 캐시 시간 (초)
        corsConfig.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    /**
     * 추가적인 CORS 헤더 처리를 위한 커스텀 웹 필터
     * 특정 상황에서 CORS 헤더가 누락되는 경우를 방지합니다.
     * 
     * 주요 처리 사항:
     * - OPTIONS 요청에 대한 명시적 처리
     * - 에러 응답에도 CORS 헤더 추가
     * - 보안 헤더 설정
     * 
     * @return WebFilter CORS 보완 필터
     */
    @Bean
    public WebFilter corsHeaderFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            
            String origin = request.getHeaders().getOrigin();
            HttpMethod method = request.getMethod();
            
            // Origin 헤더가 있고 허용된 오리진인지 확인
            if (origin != null && isAllowedOrigin(origin)) {
                // CORS 헤더 설정
                response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, 
                    String.join(",", allowedMethods));
                response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, 
                    "Origin, Content-Type, Accept, Authorization, X-Requested-With, X-CSRF-Token");
                response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    "Authorization, Content-Type, X-Gateway-Response-Time, X-Rate-Limit-Remaining");
                response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAge.toString());
                
                // 보안 헤더 추가
                response.getHeaders().add("X-Content-Type-Options", "nosniff");
                response.getHeaders().add("X-Frame-Options", "DENY");
                response.getHeaders().add("X-XSS-Protection", "1; mode=block");
                
                // Preflight 요청 처리
                if (HttpMethod.OPTIONS.equals(method)) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }
            
            return chain.filter(exchange);
        };
    }

    /**
     * 허용된 오리진인지 확인하는 헬퍼 메서드
     * 
     * @param origin 요청 오리진
     * @return boolean 허용 여부
     */
    private boolean isAllowedOrigin(String origin) {
        for (String allowedOrigin : allowedOrigins) {
            // 와일드카드 패턴 지원
            if (allowedOrigin.equals("*") || allowedOrigin.equals(origin)) {
                return true;
            }
            
            // 패턴 매칭 (예: *.yourdomain.com)
            if (allowedOrigin.startsWith("*.")) {
                String domain = allowedOrigin.substring(2);
                if (origin.endsWith("." + domain) || origin.equals(domain)) {
                    return true;
                }
            }
        }
        return false;
    }
}

