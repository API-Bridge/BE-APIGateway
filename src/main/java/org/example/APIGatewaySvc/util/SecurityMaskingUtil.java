package org.example.APIGatewaySvc.util;

import java.util.regex.Pattern;

/**
 * 보안 정보 마스킹 유틸리티
 * 로깅 및 표시 시 민감한 정보를 안전하게 마스킹
 */
public class SecurityMaskingUtil {

    // JWT 토큰 패턴 (Bearer 접두사 포함/미포함)
    private static final Pattern JWT_PATTERN = Pattern.compile(
        "(Bearer\\s+)?([A-Za-z0-9_-]+)\\.([A-Za-z0-9_-]+)\\.([A-Za-z0-9_-]+)"
    );
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    );
    
    // API 키 패턴 (예: API_KEY, SECRET_KEY 등)
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token)\\s*[:=]\\s*['\"]?([^\\s'\"]+)['\"]?",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * JWT 토큰 마스킹
     * @param text 원본 텍스트
     * @return 마스킹된 텍스트
     */
    public static String maskJwtToken(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        return JWT_PATTERN.matcher(text).replaceAll(match -> {
            String bearer = match.group(1) != null ? match.group(1) : "";
            String header = match.group(2);
            String payload = match.group(3);
            String signature = match.group(4);
            
            // 헤더와 시그니처는 일부만 표시, 페이로드는 완전 마스킹
            String maskedHeader = maskPartial(header, 4, 2);
            String maskedSignature = maskPartial(signature, 4, 2);
            
            return bearer + maskedHeader + ".[PAYLOAD_MASKED]." + maskedSignature;
        });
    }

    /**
     * 이메일 마스킹
     * @param text 원본 텍스트  
     * @return 마스킹된 텍스트
     */
    public static String maskEmail(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        return EMAIL_PATTERN.matcher(text).replaceAll(match -> {
            String username = match.group(1);
            String domain = match.group(2);
            
            String maskedUsername = maskPartial(username, 2, 1);
            return maskedUsername + "@" + domain;
        });
    }

    /**
     * API 키/시크릿 마스킹
     * @param text 원본 텍스트
     * @return 마스킹된 텍스트
     */
    public static String maskApiKeys(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        return API_KEY_PATTERN.matcher(text).replaceAll(match -> {
            String keyName = match.group(1);
            String keyValue = match.group(2);
            String delimiter = match.group().contains("=") ? "=" : ":";
            String spacing = match.group().contains(": ") ? " " : "";
            
            String maskedValue = maskPartial(keyValue, 4, 3);
            return keyName + delimiter + spacing + maskedValue;
        });
    }

    /**
     * 모든 민감 정보 마스킹 (종합)
     * @param text 원본 텍스트
     * @return 마스킹된 텍스트
     */
    public static String maskSensitiveInfo(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        String masked = maskJwtToken(text);
        masked = maskEmail(masked);
        masked = maskApiKeys(masked);
        
        return masked;
    }

    /**
     * 문자열 부분 마스킹 유틸리티
     * @param text 원본 문자열
     * @param prefixLength 앞부분 표시 길이
     * @param suffixLength 뒷부분 표시 길이
     * @return 마스킹된 문자열
     */
    public static String maskPartial(String text, int prefixLength, int suffixLength) {
        if (text == null || text.length() <= prefixLength + suffixLength) {
            return "[MASKED]";
        }
        
        String prefix = text.substring(0, Math.min(prefixLength, text.length()));
        String suffix = suffixLength > 0 && text.length() > suffixLength 
            ? text.substring(text.length() - suffixLength) 
            : "";
        
        int maskedLength = text.length() - prefix.length() - suffix.length();
        String mask = "*".repeat(Math.min(maskedLength, 8)); // 최대 8개의 * 표시
        
        return prefix + mask + suffix;
    }

    /**
     * Authorization 헤더 마스킹 (Bearer 토큰)
     * @param authHeader Authorization 헤더 값
     * @return 마스킹된 헤더 값
     */
    public static String maskAuthorizationHeader(String authHeader) {
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader;
        }
        
        String token = authHeader.substring(7); // "Bearer " 제거
        String maskedToken = maskPartial(token, 10, 10);
        
        return "Bearer " + maskedToken;
    }

    /**
     * X-Request-ID 생성 (UUID 기반)
     * @return 새로운 Request ID
     */
    public static String generateRequestId() {
        return java.util.UUID.randomUUID().toString();
    }
}