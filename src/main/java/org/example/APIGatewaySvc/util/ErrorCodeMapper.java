package org.example.APIGatewaySvc.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * HTTP 상태 코드를 비즈니스 에러 코드로 매핑하는 유틸리티 클래스
 * 
 * 매핑 규칙:
 * - 401 → UNAUTHORIZED (인증 실패)
 * - 403 → FORBIDDEN (권한 없음)
 * - 404 → NOT_FOUND (리소스 없음)
 * - 408/504 → UPSTREAM_TIMEOUT (업스트림 타임아웃)
 * - 5xx → UPSTREAM_ERROR (업스트림 서버 오류)
 * - 429 → RATE_LIMITED (요청 한도 초과)
 * - 차단된 요청 → BLOCKED (보안상 차단)
 */
public class ErrorCodeMapper {
    
    // HTTP 상태 코드별 비즈니스 에러 코드 매핑
    private static final Map<Integer, String> STATUS_CODE_MAPPING = Map.of(
        401, "UNAUTHORIZED",
        403, "FORBIDDEN", 
        404, "NOT_FOUND",
        408, "UPSTREAM_TIMEOUT",
        429, "RATE_LIMITED",
        500, "UPSTREAM_ERROR",
        502, "UPSTREAM_ERROR",
        503, "UPSTREAM_ERROR",
        504, "UPSTREAM_TIMEOUT"
    );
    
    // HTTP 상태 코드별 사용자 친화적 메시지 매핑
    private static final Map<Integer, String> STATUS_MESSAGE_MAPPING = Map.of(
        401, "인증이 필요합니다",
        403, "접근 권한이 없습니다",
        404, "요청한 리소스를 찾을 수 없습니다",
        408, "요청 처리 시간이 초과되었습니다",
        429, "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요",
        500, "서버에서 오류가 발생했습니다",
        502, "게이트웨이 오류가 발생했습니다",
        503, "서비스를 일시적으로 사용할 수 없습니다",
        504, "게이트웨이 시간 초과가 발생했습니다"
    );
    
    // 에러 타입별 분류 매핑
    private static final Map<String, String> ERROR_TYPE_MAPPING = Map.of(
        "UNAUTHORIZED", "AUTHENTICATION",
        "FORBIDDEN", "AUTHORIZATION",
        "NOT_FOUND", "CLIENT_ERROR",
        "UPSTREAM_TIMEOUT", "INFRASTRUCTURE",
        "RATE_LIMITED", "POLICY_VIOLATION",
        "UPSTREAM_ERROR", "INFRASTRUCTURE",
        "BLOCKED", "SECURITY_VIOLATION",
        "VALIDATION_ERROR", "CLIENT_ERROR"
    );
    
    /**
     * HTTP 상태 코드를 비즈니스 에러 코드로 매핑
     * 
     * @param httpStatusCode HTTP 상태 코드
     * @return 비즈니스 에러 코드
     */
    public static String mapToErrorCode(HttpStatusCode httpStatusCode) {
        int statusCodeValue = httpStatusCode.value();
        
        // 직접 매핑이 있는 경우
        if (STATUS_CODE_MAPPING.containsKey(statusCodeValue)) {
            return STATUS_CODE_MAPPING.get(statusCodeValue);
        }
        
        // 범위별 매핑
        if (statusCodeValue >= 500) {
            return "UPSTREAM_ERROR";
        } else if (statusCodeValue >= 400) {
            return "CLIENT_ERROR";
        } else if (statusCodeValue >= 300) {
            return "REDIRECT";
        } else {
            return "SUCCESS";
        }
    }
    
    /**
     * HTTP 상태 코드를 사용자 친화적 메시지로 매핑
     * 
     * @param httpStatusCode HTTP 상태 코드
     * @return 사용자 친화적 메시지
     */
    public static String mapToUserMessage(HttpStatusCode httpStatusCode) {
        int statusCodeValue = httpStatusCode.value();
        
        // 직접 매핑이 있는 경우
        if (STATUS_MESSAGE_MAPPING.containsKey(statusCodeValue)) {
            return STATUS_MESSAGE_MAPPING.get(statusCodeValue);
        }
        
        // 범위별 기본 메시지
        if (statusCodeValue >= 500) {
            return "서버에서 오류가 발생했습니다";
        } else if (statusCodeValue >= 400) {
            return "클라이언트 요청에 오류가 있습니다";
        } else if (statusCodeValue >= 300) {
            return "리다이렉션이 필요합니다";
        } else {
            return "요청이 성공적으로 처리되었습니다";
        }
    }
    
    /**
     * 에러 코드를 에러 타입으로 매핑
     * 
     * @param errorCode 비즈니스 에러 코드
     * @return 에러 타입
     */
    public static String mapToErrorType(String errorCode) {
        return ERROR_TYPE_MAPPING.getOrDefault(errorCode, "UNKNOWN");
    }
    
    /**
     * 특별한 상황 (보안 차단 등)에 대한 에러 코드 반환
     * 
     * @param reason 차단 사유
     * @return 비즈니스 에러 코드
     */
    public static String mapBlockedRequest(String reason) {
        return "BLOCKED";
    }
    
    /**
     * Rate Limiting 위반에 대한 에러 코드 반환
     * 
     * @return 비즈니스 에러 코드
     */
    public static String mapRateLimited() {
        return "RATE_LIMITED";
    }
    
    /**
     * 요청 시간 초과에 대한 에러 코드 반환
     * 
     * @return 비즈니스 에러 코드
     */
    public static String mapTimeout() {
        return "UPSTREAM_TIMEOUT";
    }
    
    /**
     * HTTP 상태가 성공인지 확인
     * 
     * @param httpStatusCode HTTP 상태 코드
     * @return 성공 여부
     */
    public static boolean isSuccessStatus(HttpStatusCode httpStatusCode) {
        return httpStatusCode.is2xxSuccessful();
    }
    
    /**
     * HTTP 상태가 클라이언트 에러인지 확인
     * 
     * @param httpStatusCode HTTP 상태 코드
     * @return 클라이언트 에러 여부
     */
    public static boolean isClientError(HttpStatusCode httpStatusCode) {
        return httpStatusCode.is4xxClientError();
    }
    
    /**
     * HTTP 상태가 서버 에러인지 확인
     * 
     * @param httpStatusCode HTTP 상태 코드
     * @return 서버 에러 여부
     */
    public static boolean isServerError(HttpStatusCode httpStatusCode) {
        return httpStatusCode.is5xxServerError();
    }
}