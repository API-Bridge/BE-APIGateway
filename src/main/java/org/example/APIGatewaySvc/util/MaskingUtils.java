package org.example.APIGatewaySvc.util;

// 민감 정보 마스킹 유틸리티
public class MaskingUtils {

    private static final String MASK = "***";

    public static String maskAuthorizationHeader(String authHeader) {
        return maskApiKey(authHeader);
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        return MASK;
    }
    // PII(Personal Identifiable Information) 마스킹 로직
    // 예: 이메일, 전화번호 등
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        return parts[0].replaceAll("\\.", "*") + "@" + parts[1];
    }
}