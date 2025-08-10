package org.example.APIGatewaySvc.exception;

import org.example.APIGatewaySvc.filter.RequestIdFilter;
import org.example.APIGatewaySvc.util.ProblemDetailsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway 전역 에러 핸들러
 * 인증/인가 실패 및 기타 예외를 RFC 7807 Problem Details 표준으로 변환
 * 
 * 주요 기능:
 * - OAuth2/JWT 인증 에러를 표준 JSON 응답으로 변환
 * - 접근 권한 에러 (403) 처리
 * - 서비스 불가 에러 (503) 처리
 * - Request ID를 통한 에러 추적성 제공
 * - 민감한 정보 마스킹으로 보안 강화
 * - 구조화된 로깅으로 모니터링 지원
 */
@Component
@Order(-2) // DefaultErrorWebExceptionHandler보다 높은 우선순위
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    /**
     * 모든 예외를 처리하여 적절한 HTTP 응답으로 변환
     * 
     * @param exchange ServerWebExchange 웹 교환 객체
     * @param ex Throwable 발생한 예외
     * @return Mono<Void> Reactive 에러 응답
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        // Request ID 추출 (간단한 방식)
        final String requestId = exchange.getResponse().getHeaders().getFirst("X-Request-ID") != null 
            ? exchange.getResponse().getHeaders().getFirst("X-Request-ID")
            : "error-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // 이미 응답이 커밋된 경우 처리 불가
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 예외 유형별 처리
        return handleSpecificException(response, ex, requestId)
                .doOnSuccess(unused -> logError(ex, requestId, exchange))
                .onErrorResume(fallbackEx -> {
                    logger.error("Error handler failed for request: {}", requestId, fallbackEx);
                    return handleFallback(response, requestId);
                });
    }

    /**
     * 예외 유형별 구체적인 처리 로직
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param ex Throwable 발생한 예외
     * @param requestId String 요청 추적 ID
     * @return Mono<Void> Reactive 에러 응답
     */
    private Mono<Void> handleSpecificException(ServerHttpResponse response, Throwable ex, String requestId) {
        
        // OAuth2 인증 실패 (JWT 토큰 오류)
        if (ex instanceof OAuth2AuthenticationException oauth2Ex) {
            String detail = "Invalid or expired JWT token";
            if (oauth2Ex.getError() != null) {
                String errorCode = oauth2Ex.getError().getErrorCode();
                if ("invalid_audience".equals(errorCode)) {
                    detail = "JWT token audience mismatch";
                } else if ("invalid_issuer".equals(errorCode)) {
                    detail = "JWT token issuer verification failed";
                }
            }
            return ProblemDetailsUtil.writeUnauthorizedResponse(response, requestId, detail);
        }

        // JWT 토큰 파싱/검증 오류
        if (ex instanceof JwtException) {
            String detail = "JWT token validation failed";
            return ProblemDetailsUtil.writeUnauthorizedResponse(response, requestId, detail);
        }

        // 일반적인 인증 실패
        if (ex instanceof AuthenticationException) {
            String detail = "Authentication required";
            return ProblemDetailsUtil.writeUnauthorizedResponse(response, requestId, detail);
        }

        // 접근 권한 부족
        if (ex instanceof AccessDeniedException) {
            String detail = "Insufficient permissions to access this resource";
            return ProblemDetailsUtil.writeForbiddenResponse(response, requestId, detail);
        }

        // Gateway Not Found (라우팅 실패)
        if (ex instanceof NotFoundException) {
            String detail = "The requested service endpoint was not found";
            return ProblemDetailsUtil.writeServiceUnavailableResponse(response, requestId, detail);
        }

        // Rate Limiting 에러 (429 Too Many Requests)
        if (isRateLimitExceeded(ex)) {
            String detail = "Too many requests. Please slow down and try again later";
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().add("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getHeaders().add("X-Request-ID", requestId);
            response.getHeaders().add("Retry-After", "60");  // 1분 후 재시도 권장
            
            return ProblemDetailsUtil.writeCustomResponse(response, HttpStatus.TOO_MANY_REQUESTS, 
                "Rate limit exceeded", detail, requestId);
        }

        // ResponseStatusException (명시적 HTTP 상태)
        if (ex instanceof ResponseStatusException responseEx) {
            HttpStatus status = HttpStatus.resolve(responseEx.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            
            return switch (status) {
                case UNAUTHORIZED -> ProblemDetailsUtil.writeUnauthorizedResponse(
                    response, requestId, responseEx.getReason());
                case FORBIDDEN -> ProblemDetailsUtil.writeForbiddenResponse(
                    response, requestId, responseEx.getReason());
                case SERVICE_UNAVAILABLE -> ProblemDetailsUtil.writeServiceUnavailableResponse(
                    response, requestId, responseEx.getReason());
                default -> ProblemDetailsUtil.writeServiceUnavailableResponse(
                    response, requestId, "Internal server error");
            };
        }

        // 연결 타임아웃 등 외부 의존성 오류
        if (isExternalDependencyError(ex)) {
            String detail = "External service temporarily unavailable";
            return ProblemDetailsUtil.writeServiceUnavailableResponse(response, requestId, detail);
        }

        // 기타 모든 예외는 503 Service Unavailable로 처리
        return ProblemDetailsUtil.writeServiceUnavailableResponse(
            response, requestId, "Service temporarily unavailable");
    }

    /**
     * Rate Limiting 관련 오류 판별
     * Redis Rate Limiter에서 발생하는 요청 제한 오류 감지
     * 
     * @param ex Throwable 예외 객체
     * @return boolean Rate Limit 초과 여부
     */
    private boolean isRateLimitExceeded(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) return false;
        
        return message.contains("Request rate limit exceeded") ||
               message.contains("Rate limit") ||
               message.contains("Too many requests") ||
               ex.getClass().getSimpleName().contains("RateLimit");
    }

    /**
     * 외부 의존성 관련 오류 판별
     * Auth0 JWKS 엔드포인트, 다운스트림 서비스 연결 오류 등
     * 
     * @param ex Throwable 예외 객체
     * @return boolean 외부 의존성 오류 여부
     */
    private boolean isExternalDependencyError(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) return false;
        
        return message.contains("Connection timeout") ||
               message.contains("Read timeout") ||
               message.contains("ConnectException") ||
               message.contains("UnknownHostException") ||
               message.contains("JWKS") ||
               message.contains("/.well-known/");
    }

    /**
     * 에러 핸들러 자체 실패 시 최후 수단 응답
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param requestId String 요청 추적 ID
     * @return Mono<Void> 기본 에러 응답
     */
    private Mono<Void> handleFallback(ServerHttpResponse response, String requestId) {
        return ProblemDetailsUtil.writeServiceUnavailableResponse(
            response, requestId, "An unexpected error occurred");
    }

    /**
     * 구조화된 에러 로깅
     * 모니터링 및 디버깅을 위한 상세 로그 기록
     * 
     * @param ex Throwable 발생한 예외
     * @param requestId String 요청 추적 ID
     * @param exchange ServerWebExchange 웹 교환 객체
     */
    private void logError(Throwable ex, String requestId, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String clientIP = getClientIP(exchange);
        
        if (ex instanceof AuthenticationException || ex instanceof AccessDeniedException) {
            // 인증/인가 오류는 INFO 레벨 (정상적인 보안 동작)
            logger.info("Security error - Request: {} {} {} from IP: {} - Error: {} - RequestID: {}",
                method, path, exchange.getRequest().getHeaders().getFirst("User-Agent"),
                clientIP, ex.getClass().getSimpleName(), requestId);
        } else {
            // 기타 오류는 ERROR 레벨 (시스템 문제 가능성)
            logger.error("Unhandled error - Request: {} {} from IP: {} - RequestID: {}",
                method, path, clientIP, requestId, ex);
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     * Proxy/Load Balancer 환경을 고려한 실제 클라이언트 IP 확인
     * 
     * @param exchange ServerWebExchange 웹 교환 객체
     * @return String 클라이언트 IP 주소
     */
    private String getClientIP(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return exchange.getRequest().getRemoteAddress() != null ? 
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}