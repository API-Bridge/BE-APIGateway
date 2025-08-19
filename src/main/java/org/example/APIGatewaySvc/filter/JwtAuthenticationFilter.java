package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JWT 토큰 필수 검증 필터
 * - 보호된 경로에 대해 Authorization 헤더에 JWT 토큰이 있는지 확인
 * - 토큰이 없는 경우 401 Unauthorized 응답 반환
 * - 공개 경로는 검증하지 않음
 * - 인증 이벤트를 Kafka로 전송
 */
@Slf4j
@Component  // JWT 인증 활성화
@Order(-1) // Security Filter보다 먼저 실행
public class JwtAuthenticationFilter implements WebFilter {

    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;

    // 토큰 검증을 하지 않을 공개 경로들
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        "/auth/**",
        "/oauth2/**", 
        "/login/**",
        "/public/**",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/webjars/**",
        "/swagger-resources/**",
        "/configuration/**",
        "/swagger-config.json",
        "/api-docs/**",
        "/favicon.ico",
        "/actuator/health",
        "/actuator/info",
        "/actuator/**",
        "/test/**",        // 모든 테스트 엔드포인트 허용
        "/error",
        "/",
        // 정적 리소스 파일들 허용
        "/integrated-test.html",
        "/*.html",         // 모든 HTML 파일 허용
        "/static/**",      // 정적 파일 경로 허용
        "/css/**",
        "/js/**",
        "/images/**",
        // User Service health check 허용
        "/gateway/users/actuator/health",
        "/gateway/users/api/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // OPTIONS 요청은 CORS preflight이므로 통과
        if ("OPTIONS".equals(request.getMethod().name())) {
            return chain.filter(exchange);
        }
        
        // 공개 경로인지 확인
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        // Gateway를 통한 각 서비스의 OpenAPI 문서 경로도 허용
        if (isOpenApiPath(path)) {
            return chain.filter(exchange);
        }
        
        // Authorization 헤더 확인
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        // JWT 토큰이 없는 경우 401 반환
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logAuthEvent(exchange, "TOKEN_MISSING", "No Authorization header or invalid format", false);
            return handleUnauthorized(exchange);
        }
        
        // JWT 토큰이 있는 경우 다음 필터로 전달 (Spring Security가 토큰 유효성 검증)
        logAuthEvent(exchange, "TOKEN_PRESENT", "JWT token found in Authorization header", true);
        
        return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                // 요청 성공 시 (토큰 검증 성공으로 간주)
                logAuthEvent(exchange, "AUTH_SUCCESS", "Request processed successfully with valid token", true);
            })
            .doOnError(throwable -> {
                // 요청 실패 시
                if (exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    logAuthEvent(exchange, "TOKEN_INVALID", "JWT token validation failed: " + throwable.getMessage(), false);
                } else {
                    logAuthEvent(exchange, "AUTH_ERROR", "Authentication error: " + throwable.getMessage(), false);
                }
            });
    }
    
    /**
     * 공개 경로인지 확인
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(publicPath -> {
                    if (publicPath.endsWith("/**")) {
                        String prefix = publicPath.substring(0, publicPath.length() - 3);
                        return path.startsWith(prefix);
                    } else if (publicPath.equals("/*.html")) {
                        // HTML 파일 패턴 매칭 (루트 경로의 HTML 파일)
                        return path.endsWith(".html") && path.lastIndexOf("/") == 0;
                    } else {
                        return path.equals(publicPath);
                    }
                });
    }
    
    /**
     * OpenAPI 문서 경로인지 확인
     */
    private boolean isOpenApiPath(String path) {
        return path.matches("/gateway/.*/v3/api-docs.*") ||
               path.matches("/gateway/.*/swagger-ui.*");
    }
    
    /**
     * 인증 이벤트 로깅
     */
    private void logAuthEvent(ServerWebExchange exchange, String eventType, String message, boolean success) {
        try {
            if (kafkaProducerService != null) {
                ServerHttpRequest request = exchange.getRequest();
                String clientIp = getClientIp(exchange);
                String userAgent = request.getHeaders().getFirst("User-Agent");
                String userId = extractUserIdFromToken(request.getHeaders().getFirst("Authorization"));
                
                Map<String, Object> authEvent = new java.util.HashMap<>();
                authEvent.put("eventType", eventType);
                authEvent.put("path", request.getURI().getPath());
                authEvent.put("method", request.getMethod().name());
                authEvent.put("userId", userId != null ? userId : "anonymous");
                authEvent.put("clientIp", clientIp);
                authEvent.put("userAgent", userAgent != null ? userAgent : "unknown");
                authEvent.put("success", success);
                authEvent.put("message", message);
                authEvent.put("timestamp", LocalDateTime.now().toString());
                authEvent.put("sessionId", getSessionId(exchange));
                authEvent.put("requestId", getRequestId(exchange));

                kafkaProducerService.sendAuthEvent(authEvent);
                log.debug("인증 이벤트 Kafka 전송 완료 - EventType: {}, UserId: {}, Success: {}", 
                         eventType, userId, success);
            }
        } catch (Exception e) {
            log.error("인증 이벤트 로깅 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * JWT 토큰에서 사용자 ID 추출
     */
    private String extractUserIdFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        
        try {
            String token = authHeader.substring(7);
            // 실제 환경에서는 JWT 라이브러리를 사용하여 토큰 파싱
            // 여기서는 간단히 토큰의 일부를 사용자 ID로 사용 (실제로는 보안상 위험)
            return "user_from_token_" + token.hashCode();
        } catch (Exception e) {
            log.debug("토큰에서 사용자 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null 
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    /**
     * 세션 ID 추출
     */
    private String getSessionId(ServerWebExchange exchange) {
        // 실제 환경에서는 세션 관리 방식에 따라 구현
        return exchange.getRequest().getId();
    }

    /**
     * 요청 ID 추출
     */
    private String getRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        return requestId != null ? requestId : exchange.getRequest().getId();
    }

    /**
     * 401 Unauthorized 응답 처리
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        
        String errorBody = """
            {
                "error": "Unauthorized",
                "message": "JWT token is required for this endpoint",
                "status": 401,
                "timestamp": "%s",
                "path": "%s"
            }
            """.formatted(
                java.time.Instant.now().toString(),
                exchange.getRequest().getURI().getPath()
            );
        
        org.springframework.core.io.buffer.DataBuffer buffer = 
            response.bufferFactory().wrap(errorBody.getBytes());
        
        return response.writeWith(Mono.just(buffer));
    }
}