package org.example.APIGatewaySvc.filter;

import org.example.APIGatewaySvc.dto.GatewayLogEvent;
import org.example.APIGatewaySvc.service.GatewayLogService;
import org.example.APIGatewaySvc.util.SecurityMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gateway 요청/응답 로깅 필터
 * 모든 요청의 시작/종료/에러를 Kafka 토픽(logs.gateway)으로 발행
 * 민감 정보 마스킹 처리 포함
 */
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayLoggingFilter.class);
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String START_TIME_KEY = "startTime";
    private static final String REQUEST_SIZE_KEY = "requestSize";
    
    private final GatewayLogService logService;

    public GatewayLoggingFilter(GatewayLogService logService) {
        this.logService = logService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Request ID 생성 및 저장
        String requestId = extractOrGenerateRequestId(exchange);
        exchange.getAttributes().put(REQUEST_ID_KEY, requestId);
        
        // 시작 시간 기록
        long startTime = Instant.now().toEpochMilli();
        exchange.getAttributes().put(START_TIME_KEY, startTime);
        
        // 요청 크기 기록 (Content-Length 헤더에서)
        String contentLength = exchange.getRequest().getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                exchange.getAttributes().put(REQUEST_SIZE_KEY, Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                // 무시
            }
        }

        // PreFilter: 요청 시작 로그
        logRequestStart(exchange, requestId);

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    // PostFilter: 요청 성공 로그
                    logRequestEnd(exchange);
                })
                .doOnError(throwable -> {
                    // 에러 발생 시 로그
                    logRequestError(exchange, throwable);
                });
    }

    /**
     * Request ID 추출 또는 생성
     * X-Request-ID 헤더가 있으면 사용, 없으면 새로 생성
     */
    private String extractOrGenerateRequestId(ServerWebExchange exchange) {
        String existingRequestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        if (existingRequestId != null && !existingRequestId.trim().isEmpty()) {
            return existingRequestId.trim();
        }
        return SecurityMaskingUtil.generateRequestId();
    }

    /**
     * 요청 시작 로그
     */
    private void logRequestStart(ServerWebExchange exchange, String requestId) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            
            // 클라이언트 IP 추출
            String clientIp = getClientIpAddress(exchange);
            
            // 라우트 ID 추출
            String routeId = extractRouteId(exchange);
            
            // 공개 API 이름 추출 (라우트 메타데이터에서)
            String publicApiName = extractPublicApiName(exchange);
            
            // 헤더 정보 수집 (민감 정보는 서비스에서 마스킹)
            Map<String, String> headers = collectImportantHeaders(request);
            
            // 사용자 ID 추출 (동기적 처리)
            String userId = null;
            try {
                userId = ReactiveSecurityContextHolder.getContext()
                    .cast(SecurityContext.class)
                    .map(securityContext -> {
                        Authentication authentication = securityContext.getAuthentication();
                        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                            return ((Jwt) authentication.getPrincipal()).getClaimAsString("sub");
                        }
                        return null;
                    })
                    .onErrorReturn(null)
                    .block(); // 동기적으로 처리
            } catch (Exception e) {
                logger.debug("Cannot extract user ID: {}", e.getMessage());
                userId = null;
            }
            
            // GatewayLogService를 통해 로그 전송
            logService.logRequestStart(
                requestId,
                request.getMethod().name(),
                request.getURI().getPath() + (request.getURI().getQuery() != null ? "?" + request.getURI().getQuery() : ""),
                clientIp,
                userId,
                headers.get("user-agent"),
                headers.get("referer"),
                routeId,
                publicApiName,
                headers
            );
                
        } catch (Exception e) {
            logger.warn("Error logging request start: {}", e.getMessage());
        }
    }

    /**
     * 요청 완료 로그
     */
    private void logRequestEnd(ServerWebExchange exchange) {
        try {
            String requestId = (String) exchange.getAttributes().get(REQUEST_ID_KEY);
            Long startTime = (Long) exchange.getAttributes().get(START_TIME_KEY);
            
            if (requestId != null && startTime != null) {
                long durationMs = Instant.now().toEpochMilli() - startTime;
                Integer status = exchange.getResponse().getStatusCode() != null ? 
                    exchange.getResponse().getStatusCode().value() : null;
                
                // 응답 크기 계산 (근사치)
                Long responseSize = calculateResponseSize(exchange);
                
                logService.logRequestEnd(requestId, status, durationMs, responseSize);
            }
        } catch (Exception e) {
            logger.warn("Error logging request end: {}", e.getMessage());
        }
    }

    /**
     * 요청 에러 로그
     */
    private void logRequestError(ServerWebExchange exchange, Throwable throwable) {
        try {
            String requestId = (String) exchange.getAttributes().get(REQUEST_ID_KEY);
            Long startTime = (Long) exchange.getAttributes().get(START_TIME_KEY);
            
            if (requestId != null && startTime != null) {
                long durationMs = Instant.now().toEpochMilli() - startTime;
                Integer status = exchange.getResponse().getStatusCode() != null ? 
                    exchange.getResponse().getStatusCode().value() : 500;
                
                String errorMessage = throwable.getMessage();
                String errorType = throwable.getClass().getSimpleName();
                
                logService.logRequestError(requestId, status, durationMs, errorMessage, errorType);
            }
        } catch (Exception e) {
            logger.warn("Error logging request error: {}", e.getMessage());
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp.trim();
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * 라우트 ID 추출
     */
    private String extractRouteId(ServerWebExchange exchange) {
        // Spring Cloud Gateway의 라우트 ID 추출
        return Optional.ofNullable(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR))
            .map(route -> route.toString())
            .orElse("unknown");
    }

    /**
     * 공개 API 이름 추출
     */
    private String extractPublicApiName(ServerWebExchange exchange) {
        // 라우트 메타데이터나 경로를 기반으로 API 이름 추출
        String path = exchange.getRequest().getURI().getPath();
        
        if (path.startsWith("/gateway/users")) {
            return "Users API";
        } else if (path.startsWith("/gateway/apimgmt")) {
            return "API Management";
        } else if (path.startsWith("/gateway/customapi")) {
            return "Custom API Management";
        } else if (path.startsWith("/gateway/aifeature")) {
            return "AI Feature API";
        } else if (path.startsWith("/gateway/sysmgmt")) {
            return "System Management API";
        } else if (path.startsWith("/public")) {
            return "Public API";
        } else if (path.startsWith("/internal")) {
            return "Internal API";
        }
        
        return null;
    }

    /**
     * 중요한 헤더 정보 수집
     */
    private Map<String, String> collectImportantHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        // 로깅에 필요한 주요 헤더들만 수집
        String[] importantHeaders = {
            "authorization", "x-api-key", "user-agent", "referer", 
            "content-type", "accept", "x-forwarded-for", "x-real-ip",
            "x-request-id", "origin", "host"
        };
        
        for (String headerName : importantHeaders) {
            String value = request.getHeaders().getFirst(headerName);
            if (value != null) {
                headers.put(headerName.toLowerCase(), value);
            }
        }
        
        return headers;
    }

    /**
     * 응답 크기 계산 (근사치)
     */
    private Long calculateResponseSize(ServerWebExchange exchange) {
        // Content-Length 응답 헤더에서 추출
        String contentLength = exchange.getResponse().getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                // 무시
            }
        }
        
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // BlockCheckFilter 다음, 다른 필터보다 먼저 실행
    }
}