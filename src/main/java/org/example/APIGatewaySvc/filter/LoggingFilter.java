package org.example.APIGatewaySvc.filter;

import org.example.APIGatewaySvc.util.SecurityMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * API Gateway 요청/응답 로깅 필터
 * 민감한 정보(JWT 토큰, 개인정보)를 마스킹하여 안전하게 로깅
 */
@Component
@Order(-90) // RequestIdFilter 다음에 실행
public class LoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    
    // 로깅에서 제외할 경로
    private static final List<String> EXCLUDED_PATHS = List.of(
        "/actuator/health",
        "/actuator/info",
        "/favicon.ico"
    );
    
    // 마스킹할 헤더 목록
    private static final List<String> SENSITIVE_HEADERS = List.of(
        "authorization",
        "x-api-key", 
        "x-auth-token",
        "cookie",
        "set-cookie"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // 제외 경로 확인
        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        
        long startTime = System.currentTimeMillis();
        
        // 요청 로깅
        logRequest(request);
        
        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    // 응답 로깅
                    long duration = System.currentTimeMillis() - startTime;
                    logResponse(exchange.getResponse(), duration);
                })
                .doOnError(throwable -> {
                    // 에러 로깅
                    long duration = System.currentTimeMillis() - startTime;
                    logError(request, throwable, duration);
                });
    }

    /**
     * 요청 정보 로깅 (민감한 정보 마스킹)
     */
    private void logRequest(ServerHttpRequest request) {
        try {
            String method = request.getMethod().name();
            String path = request.getPath().value();
            String queryString = request.getURI().getQuery();
            String remoteAddress = getRemoteAddress(request);
            
            // 헤더 정보 (민감한 헤더 마스킹)
            StringBuilder headers = new StringBuilder();
            request.getHeaders().forEach((name, values) -> {
                String headerName = name.toLowerCase();
                if (SENSITIVE_HEADERS.contains(headerName)) {
                    // 민감한 헤더는 마스킹
                    values.forEach(value -> {
                        String maskedValue = SecurityMaskingUtil.maskSensitiveInfo(value);
                        headers.append(name).append(": ").append(maskedValue).append("; ");
                    });
                } else {
                    // 일반 헤더는 그대로 (단, 값이 너무 길면 잘라내기)
                    values.forEach(value -> {
                        String displayValue = value.length() > 100 ? value.substring(0, 100) + "..." : value;
                        headers.append(name).append(": ").append(displayValue).append("; ");
                    });
                }
            });
            
            // 로그 출력
            log.info("==> HTTP {} {} {} | Remote: {} | Headers: [{}]",
                    method,
                    path,
                    queryString != null ? "?" + queryString : "",
                    remoteAddress,
                    headers.toString());
            
        } catch (Exception e) {
            log.warn("요청 로깅 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 응답 정보 로깅
     */
    private void logResponse(ServerHttpResponse response, long duration) {
        try {
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
            String statusText = response.getStatusCode() != null ? response.getStatusCode().toString() : "Unknown";
            
            // 응답 헤더 (민감한 헤더 마스킹)
            StringBuilder headers = new StringBuilder();
            response.getHeaders().forEach((name, values) -> {
                String headerName = name.toLowerCase();
                if (SENSITIVE_HEADERS.contains(headerName)) {
                    values.forEach(value -> {
                        String maskedValue = SecurityMaskingUtil.maskSensitiveInfo(value);
                        headers.append(name).append(": ").append(maskedValue).append("; ");
                    });
                } else {
                    values.forEach(value -> {
                        String displayValue = value.length() > 100 ? value.substring(0, 100) + "..." : value;
                        headers.append(name).append(": ").append(displayValue).append("; ");
                    });
                }
            });
            
            log.info("<== HTTP {} {} | Duration: {}ms | Headers: [{}]",
                    statusCode,
                    statusText,
                    duration,
                    headers.toString());
            
        } catch (Exception e) {
            log.warn("응답 로깅 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 에러 정보 로깅
     */
    private void logError(ServerHttpRequest request, Throwable throwable, long duration) {
        try {
            String method = request.getMethod().name();
            String path = request.getPath().value();
            String errorMessage = SecurityMaskingUtil.maskSensitiveInfo(throwable.getMessage());
            
            log.error("!!! HTTP {} {} | Duration: {}ms | Error: {} - {}",
                    method,
                    path,
                    duration,
                    throwable.getClass().getSimpleName(),
                    errorMessage);
            
        } catch (Exception e) {
            log.warn("에러 로깅 중 오류 발생: {}", e.getMessage());
        }
    }

    /**
     * 원격 주소 추출 (프록시 고려)
     */
    private String getRemoteAddress(ServerHttpRequest request) {
        // X-Forwarded-For 헤더 확인 (프록시/로드밸런서 환경)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // 첫 번째 IP 주소 반환
            return xForwardedFor.split(",")[0].trim();
        }
        
        // X-Real-IP 헤더 확인
        String xRealIP = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.trim().isEmpty()) {
            return xRealIP.trim();
        }
        
        // 직접 연결 주소
        return request.getRemoteAddress() != null 
            ? request.getRemoteAddress().getAddress().getHostAddress() 
            : "unknown";
    }
}