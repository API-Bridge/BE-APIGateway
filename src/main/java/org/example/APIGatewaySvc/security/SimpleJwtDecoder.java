package org.example.APIGatewaySvc.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * 간단한 JWT 디코더 (서명 검증 없이 페이로드만 추출)
 * 디버깅 및 테스트 용도
 */
@Component
public class SimpleJwtDecoder {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * JWT 토큰에서 페이로드만 추출 (서명 검증 안함)
     */
    public JsonNode decodePayload(String token) {
        try {
            // 토큰 정제
            token = token.trim().replaceAll("\\s+", "");
            
            // JWT 형식 확인 (3개 부분)
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format. Expected 3 parts, got: " + parts.length);
            }
            
            // 헤더 디코딩 (디버깅용)
            JsonNode header = parseBase64Json(parts[0]);
            System.out.println("JWT Header: " + header.toString());
            
            // 페이로드 디코딩
            JsonNode payload = parseBase64Json(parts[1]);
            System.out.println("JWT Payload: " + payload.toString());
            
            return payload;
            
        } catch (Exception e) {
            System.err.println("JWT 디코딩 실패: " + e.getMessage());
            throw new IllegalArgumentException("Failed to decode JWT: " + e.getMessage(), e);
        }
    }
    
    /**
     * Base64 URL 디코딩 후 JSON 파싱
     */
    private JsonNode parseBase64Json(String base64) throws Exception {
        // Base64 URL 디코딩
        byte[] decoded = Base64.getUrlDecoder().decode(base64);
        String jsonString = new String(decoded);
        System.out.println("Decoded JSON: " + jsonString);
        
        // JSON 파싱
        return objectMapper.readTree(jsonString);
    }
    
    /**
     * Audience 검증 (간단 버전)
     */
    public boolean validateAudience(JsonNode payload, String expectedAudience, String clientId) {
        JsonNode audNode = payload.get("aud");
        if (audNode == null) {
            return false;
        }
        
        if (audNode.isArray()) {
            for (JsonNode aud : audNode) {
                String audValue = aud.asText();
                if (expectedAudience.equals(audValue) || clientId.equals(audValue)) {
                    return true;
                }
            }
        } else {
            String audValue = audNode.asText();
            return expectedAudience.equals(audValue) || clientId.equals(audValue);
        }
        
        return false;
    }
}