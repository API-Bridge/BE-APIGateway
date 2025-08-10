package org.example.APIGatewaySvc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * RFC 7807 Problem Details 표준 에러 응답 생성 유틸리티
 * Spring Cloud Gateway에서 발생하는 인증/인가 오류를 표준 JSON 형식으로 변환
 * 
 * 주요 기능:
 * - RFC 7807 표준 준수 에러 응답 생성
 * - HTTP 상태 코드별 적절한 에러 메시지 제공
 * - Request ID 포함으로 에러 추적성 향상
 * - 민감한 정보 노출 방지
 * - WebFlux Reactive 스트림 호환
 */
public class ProblemDetailsUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 인증 실패 (401 Unauthorized) Problem Details 응답 생성
     * JWT 토큰이 없거나 유효하지 않은 경우 사용
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param requestId 요청 추적 ID
     * @param detail 상세 에러 메시지
     * @return Mono<Void> Reactive 응답 스트림
     */
    public static Mono<Void> writeUnauthorizedResponse(ServerHttpResponse response, 
                                                      String requestId, 
                                                      String detail) {
        return writeProblemDetailsResponse(response, HttpStatus.UNAUTHORIZED, 
                "Authentication failed", detail, requestId);
    }

    /**
     * 권한 부족 (403 Forbidden) Problem Details 응답 생성
     * JWT 토큰은 유효하지만 리소스 접근 권한이 없는 경우 사용
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param requestId 요청 추적 ID
     * @param detail 상세 에러 메시지
     * @return Mono<Void> Reactive 응답 스트림
     */
    public static Mono<Void> writeForbiddenResponse(ServerHttpResponse response, 
                                                   String requestId, 
                                                   String detail) {
        return writeProblemDetailsResponse(response, HttpStatus.FORBIDDEN, 
                "Access denied", detail, requestId);
    }

    /**
     * 서비스 불가 (503 Service Unavailable) Problem Details 응답 생성
     * 외부 의존성 (Auth0 JWKS, 다운스트림 서비스) 장애 시 사용
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param requestId 요청 추적 ID
     * @param detail 상세 에러 메시지
     * @return Mono<Void> Reactive 응답 스트림
     */
    public static Mono<Void> writeServiceUnavailableResponse(ServerHttpResponse response, 
                                                            String requestId, 
                                                            String detail) {
        return writeProblemDetailsResponse(response, HttpStatus.SERVICE_UNAVAILABLE, 
                "Service temporarily unavailable", detail, requestId);
    }

    /**
     * 커스텀 HTTP 상태 코드 Problem Details 응답 생성
     * Rate Limiting, Gateway Timeout 등 다양한 상황에 대응
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param status HTTP 상태 코드
     * @param title 에러 타입 제목
     * @param detail 상세 에러 메시지
     * @param requestId 요청 추적 ID
     * @return Mono<Void> Reactive 응답 스트림
     */
    public static Mono<Void> writeCustomResponse(ServerHttpResponse response,
                                               HttpStatus status,
                                               String title,
                                               String detail,
                                               String requestId) {
        return writeProblemDetailsResponse(response, status, title, detail, requestId);
    }

    /**
     * 일반적인 Problem Details 응답 생성 메서드
     * RFC 7807 표준을 따르는 JSON 에러 응답을 생성하고 반환
     * 
     * @param response ServerHttpResponse 응답 객체
     * @param status HTTP 상태 코드
     * @param title 에러 타입 제목
     * @param detail 상세 에러 메시지
     * @param requestId 요청 추적 ID
     * @return Mono<Void> Reactive 응답 스트림
     */
    private static Mono<Void> writeProblemDetailsResponse(ServerHttpResponse response, 
                                                         HttpStatus status,
                                                         String title, 
                                                         String detail, 
                                                         String requestId) {
        // HTTP 응답 상태 및 헤더 설정
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getHeaders().add("X-Request-ID", requestId);

        // Problem Details JSON 구조 생성
        Map<String, Object> problemDetails = new HashMap<>();
        problemDetails.put("type", "about:blank");  // RFC 7807 기본값
        problemDetails.put("title", title);
        problemDetails.put("status", status.value());
        problemDetails.put("detail", sanitizeErrorMessage(detail));  // 민감정보 마스킹
        problemDetails.put("instance", requestId);
        problemDetails.put("timestamp", Instant.now().toString());

        try {
            // JSON 직렬화 및 응답 스트림 생성
            String jsonResponse = objectMapper.writeValueAsString(problemDetails);
            DataBuffer buffer = response.bufferFactory()
                    .wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            // JSON 직렬화 실패 시 기본 응답
            String fallbackResponse = String.format(
                    "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"instance\":\"%s\",\"timestamp\":\"%s\"}",
                    title, status.value(), requestId, Instant.now().toString()
            );
            DataBuffer buffer = response.bufferFactory()
                    .wrap(fallbackResponse.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
    }

    /**
     * 에러 메시지에서 민감한 정보 마스킹
     * JWT 토큰, 개인정보 등이 에러 응답에 노출되지 않도록 방지
     * 
     * @param message 원본 에러 메시지
     * @return String 마스킹된 안전한 에러 메시지
     */
    private static String sanitizeErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "An error occurred";
        }

        // JWT 토큰 패턴 마스킹 (Bearer 토큰)
        String sanitized = message.replaceAll("Bearer [A-Za-z0-9\\-_=]+\\.[A-Za-z0-9\\-_=]+\\.?[A-Za-z0-9\\-_.+/=]*", "Bearer [REDACTED]");
        
        // 기타 토큰 패턴 마스킹
        sanitized = sanitized.replaceAll("token=[A-Za-z0-9\\-_=]+", "token=[REDACTED]");
        
        // 이메일 패턴 부분 마스킹
        sanitized = sanitized.replaceAll("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", "$1@[REDACTED]");
        
        // 길이 제한 (과도한 정보 노출 방지)
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 197) + "...";
        }

        return sanitized;
    }
}