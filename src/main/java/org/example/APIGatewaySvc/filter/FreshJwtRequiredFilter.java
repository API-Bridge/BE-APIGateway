package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 민감한 작업(계정 차단/해제)에 대해 최신 JWT 토큰만 허용하는 필터 - 임시 비활성화
 * JWT 중복 검증 문제 해결을 위해 일시적으로 비활성화
 */
@Slf4j
// @Component  // 임시 비활성화
public class FreshJwtRequiredFilter implements GlobalFilter, Ordered {

    // 민감한 작업으로 간주할 최대 JWT 나이 (분)
    private static final int MAX_JWT_AGE_MINUTES = 5;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // 민감한 작업인지 확인
        if (!isSensitiveOperation(path, method)) {
            return chain.filter(exchange);
        }

        log.debug("민감한 작업 감지: {} {}", method, path);

        // JWT 토큰에서 발급 시간 확인
        return ReactiveSecurityContextHolder.getContext()
            .cast(org.springframework.security.core.context.SecurityContext.class)
            .flatMap(securityContext -> {
                var authentication = securityContext.getAuthentication();

                if (authentication == null || !authentication.isAuthenticated()) {
                    return rejectRequest(exchange, "인증되지 않은 요청입니다");
                }

                if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
                    return rejectRequest(exchange, "JWT 토큰이 필요합니다");
                }

                // JWT 발급 시간 확인
                Instant issuedAt = jwt.getIssuedAt();
                if (issuedAt == null) {
                    return rejectRequest(exchange, "JWT 토큰에 발급 시간이 없습니다");
                }

                Instant now = Instant.now();
                long minutesOld = ChronoUnit.MINUTES.between(issuedAt, now);

                if (minutesOld > MAX_JWT_AGE_MINUTES) {
                    log.warn("오래된 JWT 토큰으로 민감한 작업 시도: {}분 전 발급, 경로: {}", minutesOld, path);
                    return rejectRequest(exchange,
                        String.format("민감한 작업을 위해서는 %d분 이내에 발급된 최신 JWT 토큰이 필요합니다. (현재 토큰: %d분 전 발급)",
                                    MAX_JWT_AGE_MINUTES, minutesOld));
                }

                log.debug("최신 JWT 토큰 확인됨: {}분 전 발급", minutesOld);
                return chain.filter(exchange);
            })
            .switchIfEmpty(rejectRequest(exchange, "보안 컨텍스트를 찾을 수 없습니다"));
    }

    /**
     * 민감한 작업인지 판단
     * 계정 차단/해제 관련 엔드포인트를 대상으로 함
     */
    private boolean isSensitiveOperation(String path, String method) {
        // User Service의 계정 차단/해제 관련 엔드포인트
        if (path.matches(".*/admin/users/.*/block") ||
            path.matches(".*/admin/users/.*/unblock")) {
            return true;
        }

        // 계정 삭제도 민감한 작업으로 분류
        if (path.matches(".*/admin/users/.*") && "DELETE".equals(method)) {
            return true;
        }

        // 기타 민감한 관리자 작업들
        if (path.contains("/admin/") && ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return path.matches(".*/admin/users/.*/.*") || // 사용자 관련 모든 변경 작업
                   path.contains("/admin/system/") ||        // 시스템 설정 변경
                   path.contains("/admin/security/");       // 보안 설정 변경
        }

        return false;
    }

    /**
     * 요청 거부 응답 생성
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json");

        String errorResponse = String.format("""
            {
                "error": "Fresh JWT Required",
                "message": "%s",
                "status": 403,
                "timestamp": "%s",
                "path": "%s",
                "code": "FRESH_JWT_REQUIRED"
            }
            """,
            message,
            Instant.now().toString(),
            exchange.getRequest().getURI().getPath()
        );

        org.springframework.core.io.buffer.DataBuffer buffer =
            response.bufferFactory().wrap(errorResponse.getBytes());

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 인증 필터 이후, 다른 비즈니스 필터 이전에 실행
        return -100;
    }
}