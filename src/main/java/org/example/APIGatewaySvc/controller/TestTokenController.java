package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/public/test-tokens")
@Profile({"test", "local"})
@ConditionalOnProperty(name = "jwt.test-mode", havingValue = "true")
@Tag(name = "Test JWT Tokens", description = "로컬 개발용 JWT 토큰 생성 API (테스트 전용)")
public class TestTokenController {

    private static final String TEST_SECRET = "test-secret-key-for-local-development-only-do-not-use-in-production";

    @PostMapping("/generate")
    @Operation(summary = "테스트용 JWT 토큰 생성", description = "로컬 테스트용 JWT 토큰 생성 (1시간 유효)")
    @ApiResponse(responseCode = "200", description = "JWT 토큰 생성 성공")
    public ResponseEntity<Map<String, Object>> generateTestToken(
            @RequestBody(required = false) Map<String, Object> customClaims) {

        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + 3600; // 1시간 후 만료

            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", "test-user-123");
            payload.put("iat", now);
            payload.put("exp", exp);
            payload.put("email", "test@example.com");
            payload.put("name", "테스트 사용자");
            payload.put("aud", "https://api.api-bridge.com");
            payload.put("iss", "https://api-bridge.us.auth0.com/");

            if (customClaims != null) {
                customClaims.forEach((key, value) -> {
                    if (!("sub".equals(key) || "iat".equals(key) || "exp".equals(key))) {
                        payload.put(key, value);
                    }
                });
            }

            String token = createJwtToken(payload);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("type", "Bearer");
            response.put("expiresIn", 3600);
            response.put("expiresAt", Instant.ofEpochSecond(exp).toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "토큰 생성 실패");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/quick")
    @Operation(summary = "빠른 테스트 토큰 생성", description = "GET 요청으로 기본 테스트 토큰 생성")
    public ResponseEntity<Map<String, Object>> generateQuickToken(
            @RequestParam(defaultValue = "test-user-123") @Parameter(description = "사용자 ID") String userId,
            @RequestParam(defaultValue = "test@example.com") @Parameter(description = "이메일") String email,
            @RequestParam(defaultValue = "테스트 사용자") @Parameter(description = "사용자 이름") String name) {

        Map<String, Object> customClaims = new HashMap<>();
        customClaims.put("sub", userId);
        customClaims.put("email", email);
        customClaims.put("name", name);

        return generateTestToken(customClaims);
    }

    @GetMapping("/validate")
    @Operation(summary = "JWT 토큰 검증", description = "토큰 유효성 검증 및 디코딩")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestParam @Parameter(description = "검증할 JWT 토큰") String token) {

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("잘못된 JWT 형식");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

            String expectedSignature = createSignature(parts[0] + "." + parts[1]);
            boolean isValid = expectedSignature.equals(parts[2]);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("header", headerJson);
            response.put("payload", payloadJson);
            response.put("message", isValid ? "토큰이 유효합니다" : "토큰 서명이 유효하지 않습니다");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("valid", false);
            errorResponse.put("error", "토큰 검증 실패");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private String createJwtToken(Map<String, Object> payload) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"typ\":\"JWT\",\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));

        StringBuilder payloadJson = new StringBuilder("{");
        payload.forEach((key, value) -> {
            if (payloadJson.length() > 1) payloadJson.append(",");
            if (value instanceof String) {
                payloadJson.append("\"").append(key).append("\":\"").append(value).append("\"");
            } else {
                payloadJson.append("\"").append(key).append("\":").append(value);
            }
        });
        payloadJson.append("}");

        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.toString().getBytes(StandardCharsets.UTF_8));

        String data = header + "." + encodedPayload;
        String signature = createSignature(data);

        return data + "." + signature;
    }

    private String createSignature(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }
}