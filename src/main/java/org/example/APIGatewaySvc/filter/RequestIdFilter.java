package org.example.APIGatewaySvc.filter;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * HTTP 요청별 고유 추적 ID를 생성하고 관리하는 WebFilter
 * Spring Cloud Gateway에서 분산 시스템 추적성 향상 및 로깅 개선
 * 
 * 주요 기능:
 * - 모든 HTTP 요청에 대해 고유한 Request ID 생성
 * - 클라이언트가 제공한 X-Request-ID 헤더 우선 사용
 * - Reactive Context를 통한 요청 추적 ID 전파
 * - 응답 헤더에 Request ID 포함으로 클라이언트 디버깅 지원
 * - SLF4J MDC를 통한 로깅 컨텍스트 설정
 */
@Component
@Order(-100) // SecurityWebFilterChain보다 먼저 실행
public class RequestIdFilter implements WebFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_CONTEXT_KEY = "requestId";

    /**
     * 모든 HTTP 요청을 필터링하여 Request ID를 생성하고 컨텍스트에 설정
     * 
     * @param exchange ServerWebExchange 웹 교환 객체
     * @param chain WebFilterChain 필터 체인
     * @return Mono<Void> Reactive 필터 체인 계속 실행
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Request ID 생성 또는 클라이언트 제공 값 사용
        String requestId = extractOrGenerateRequestId(request);
        
        // 응답 헤더에 Request ID 추가 (클라이언트 디버깅 지원)
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);
        
        // Request ID를 Reactive Context에 저장하여 전체 요청 처리 과정에서 접근 가능하도록 설정
        return chain.filter(exchange)
                .contextWrite(Context.of(REQUEST_ID_CONTEXT_KEY, requestId))
                // SLF4J MDC 설정 (로깅용)
                .doOnEach(signal -> {
                    if (signal.hasValue() || signal.hasError() || signal.isOnComplete()) {
                        MDC.put("requestId", requestId);
                    }
                })
                // 요청 완료 후 MDC 정리
                .doFinally(signalType -> MDC.remove("requestId"));
    }

    /**
     * 클라이언트가 제공한 Request ID를 추출하거나 새로운 ID 생성
     * 
     * @param request ServerHttpRequest HTTP 요청 객체
     * @return String Request ID
     */
    private String extractOrGenerateRequestId(ServerHttpRequest request) {
        // 클라이언트가 제공한 X-Request-ID 헤더 확인
        String clientRequestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        
        if (clientRequestId != null && !clientRequestId.trim().isEmpty()) {
            // 클라이언트 제공 ID 검증 및 정제
            return sanitizeRequestId(clientRequestId);
        }
        
        // 새로운 UUID 기반 Request ID 생성
        return generateNewRequestId();
    }

    /**
     * 클라이언트가 제공한 Request ID를 검증하고 정제
     * 보안상 위험한 문자나 과도하게 긴 값 방지
     * 
     * @param clientRequestId 클라이언트 제공 Request ID
     * @return String 검증된 Request ID
     */
    private String sanitizeRequestId(String clientRequestId) {
        // 길이 제한 (최대 36자, UUID 길이 기준)
        if (clientRequestId.length() > 36) {
            clientRequestId = clientRequestId.substring(0, 36);
        }
        
        // 알파벳, 숫자, 하이픈만 허용 (UUID 형식 준수)
        String sanitized = clientRequestId.replaceAll("[^a-zA-Z0-9\\-]", "");
        
        // 최소 길이 확인
        if (sanitized.length() < 8) {
            return generateNewRequestId();
        }
        
        return sanitized;
    }

    /**
     * UUID 기반 새로운 Request ID 생성
     * 
     * @return String 새로운 UUID Request ID
     */
    private String generateNewRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Reactive Context에서 현재 Request ID 추출 (정적 유틸리티 메서드)
     * 다른 컴포넌트에서 Request ID에 접근할 때 사용
     * 
     * @param context Reactor Context
     * @return String Request ID (없으면 기본값)
     */
    public static String getRequestId(Context context) {
        return context.getOrDefault(REQUEST_ID_CONTEXT_KEY, "unknown");
    }
}