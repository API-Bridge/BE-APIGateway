package org.example.APIGatewaySvc.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityMaskingUtil 테스트
 * JWT 토큰, 이메일, API 키 등 민감한 정보 마스킹 기능 검증
 */
@DisplayName("보안 마스킹 유틸리티 테스트")
class SecurityMaskingUtilTest {

    @Test
    @DisplayName("JWT 토큰 마스킹 - Bearer 접두사 포함")
    void testMaskJwtTokenWithBearer() {
        String token = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        String masked = SecurityMaskingUtil.maskJwtToken(token);
        
        assertTrue(masked.startsWith("Bearer "));
        assertTrue(masked.contains("[PAYLOAD_MASKED]"));
        assertFalse(masked.contains("eyJzdWI"));
        System.out.println("Original: " + token);
        System.out.println("Masked: " + masked);
    }

    @Test
    @DisplayName("JWT 토큰 마스킹 - Bearer 접두사 없음")
    void testMaskJwtTokenWithoutBearer() {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        String masked = SecurityMaskingUtil.maskJwtToken(token);
        
        assertTrue(masked.contains("[PAYLOAD_MASKED]"));
        assertFalse(masked.contains("eyJzdWI"));
    }

    @Test
    @DisplayName("이메일 마스킹")
    void testMaskEmail() {
        String text = "사용자 이메일: john.doe@example.com, 관리자: admin@test.org";
        
        String masked = SecurityMaskingUtil.maskEmail(text);
        
        assertTrue(masked.contains("jo*****e@example.com"));
        assertTrue(masked.contains("ad**n@test.org"));
        assertFalse(masked.contains("john.doe"));
        System.out.println("Original: " + text);
        System.out.println("Masked: " + masked);
    }

    @Test
    @DisplayName("API 키 마스킹")
    void testMaskApiKeys() {
        String text = "API_KEY=abc123def456ghi789 SECRET_KEY: xyz987uvw654rst321";
        
        String masked = SecurityMaskingUtil.maskApiKeys(text);
        
        assertTrue(masked.contains("abc1********789"));
        assertTrue(masked.contains("xyz9********321"));
        assertFalse(masked.contains("abc123def456ghi789"));
        System.out.println("Original: " + text);
        System.out.println("Masked: " + masked);
    }

    @Test
    @DisplayName("Authorization 헤더 마스킹")
    void testMaskAuthorizationHeader() {
        String authHeader = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        String masked = SecurityMaskingUtil.maskAuthorizationHeader(authHeader);
        
        assertTrue(masked.startsWith("Bearer "));
        assertTrue(masked.contains("********"));
        assertFalse(masked.equals(authHeader));
        System.out.println("Original: " + authHeader);
        System.out.println("Masked: " + masked);
    }

    @Test
    @DisplayName("모든 민감 정보 종합 마스킹")
    void testMaskAllSensitiveInfo() {
        String text = """
                사용자 로그인: john.doe@example.com
                Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature
                API_KEY=secret123456
                응답 데이터를 반환합니다.
                """;
        
        String masked = SecurityMaskingUtil.maskSensitiveInfo(text);
        
        // 이메일 마스킹 확인
        assertTrue(masked.contains("jo*****e@example.com"));
        
        // JWT 토큰 마스킹 확인
        assertTrue(masked.contains("[PAYLOAD_MASKED]"));
        
        // API 키 마스킹 확인
        assertTrue(masked.contains("secr*****456"));
        
        // 일반 텍스트는 그대로 유지
        assertTrue(masked.contains("응답 데이터를 반환합니다."));
        
        System.out.println("Original:");
        System.out.println(text);
        System.out.println("Masked:");
        System.out.println(masked);
    }

    @Test
    @DisplayName("null 및 빈 문자열 처리")
    void testNullAndEmptyStrings() {
        assertNull(SecurityMaskingUtil.maskJwtToken(null));
        assertEquals("", SecurityMaskingUtil.maskJwtToken(""));
        assertEquals("   ", SecurityMaskingUtil.maskJwtToken("   "));
        
        assertNull(SecurityMaskingUtil.maskEmail(null));
        assertEquals("", SecurityMaskingUtil.maskEmail(""));
        
        assertNull(SecurityMaskingUtil.maskApiKeys(null));
        assertEquals("", SecurityMaskingUtil.maskApiKeys(""));
    }

    @Test
    @DisplayName("JWT 토큰이 아닌 문자열은 그대로 유지")
    void testNonJwtStringsRemainUnchanged() {
        String normalText = "이것은 일반적인 텍스트입니다.";
        String masked = SecurityMaskingUtil.maskJwtToken(normalText);
        
        assertEquals(normalText, masked);
    }

    @Test
    @DisplayName("이메일이 아닌 @ 포함 문자열은 그대로 유지")
    void testNonEmailStringsWithAtSymbolRemainUnchanged() {
        String normalText = "Twitter: @username, GitHub: @developer";
        String masked = SecurityMaskingUtil.maskEmail(normalText);
        
        assertEquals(normalText, masked);
    }

    @Test
    @DisplayName("Request ID 생성")
    void testGenerateRequestId() {
        String requestId1 = SecurityMaskingUtil.generateRequestId();
        String requestId2 = SecurityMaskingUtil.generateRequestId();
        
        assertNotNull(requestId1);
        assertNotNull(requestId2);
        assertNotEquals(requestId1, requestId2);
        
        // UUID 형식 확인 (36자리, 하이픈 포함)
        assertEquals(36, requestId1.length());
        assertTrue(requestId1.matches("[a-f0-9-]+"));
        
        System.out.println("Generated Request ID: " + requestId1);
    }

    @Test
    @DisplayName("짧은 토큰 마스킹")
    void testShortTokenMasking() {
        String shortToken = "abc.def.ghi";
        String masked = SecurityMaskingUtil.maskJwtToken(shortToken);
        
        assertTrue(masked.contains("[PAYLOAD_MASKED]"));
        System.out.println("Short token masked: " + masked);
    }

    @Test
    @DisplayName("매우 긴 JWT 토큰 마스킹")
    void testVeryLongJwtTokenMasking() {
        String longToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRlc3Qta2V5In0." +
                "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjQyNjIyLCJhdWQiOiJ0ZXN0LWF1ZGllbmNlIiwiaXNzIjoidGVzdC1pc3N1ZXIifQ." +
                "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5cVLWGBWrMvtnoGvTbVstACBNrKp6_2QgU3H4S6IZ9MiLgZNkUECNH6wQYE8omSnLwJJl7nF3uXxG9uGbP1hYqUBD1kNEGJnBEFDtZJLm2BEvWSdm0U9qN6w-AFTBzSzks";
        
        String masked = SecurityMaskingUtil.maskJwtToken(longToken);
        
        assertTrue(masked.contains("[PAYLOAD_MASKED]"));
        assertTrue(masked.length() < longToken.length());
        System.out.println("Long token length: " + longToken.length());
        System.out.println("Masked token length: " + masked.length());
    }
}