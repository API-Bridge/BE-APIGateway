package org.example.APIGatewaySvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 스모크 테스트를 위한 테스트 컨트롤러
 * 
 * 인증/인가 동작을 확인하기 위한 엔드포인트들을 제공합니다.
 */
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 공개 엔드포인트 - 인증 불필요
     * 스모크 테스트 1: GET /test/public → 200 (비인증 접근 가능)
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Public endpoint accessed successfully");
        response.put("authenticated", false);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 보호된 엔드포인트 - JWT 인증 필요
     * 스모크 테스트 2: GET /test/protected (토큰 없음) → 401
     * 스모크 테스트 3: GET /test/protected (유효한 Auth0 Access Token) → 200
     */
    @GetMapping("/protected")
    public Mono<ResponseEntity<Map<String, Object>>> protectedEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Protected endpoint accessed successfully");
        response.put("authenticated", true);
        response.put("principal", authentication.getName());
        response.put("authorities", authentication.getAuthorities());
        
        // JWT 토큰 정보
        if (jwt != null) {
            response.put("jwt", Map.of(
                "subject", jwt.getSubject(),
                "issuer", jwt.getIssuer(),
                "audience", jwt.getAudience(),
                "expiresAt", jwt.getExpiresAt(),
                "issuedAt", jwt.getIssuedAt(),
                "claims", jwt.getClaims()
            ));
        }
        
        response.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 권한 확인 엔드포인트 - 특정 권한 필요
     */
    @GetMapping("/admin")
    public Mono<ResponseEntity<Map<String, Object>>> adminEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Admin endpoint accessed successfully");
        response.put("authenticated", true);
        response.put("principal", authentication.getName());
        response.put("authorities", authentication.getAuthorities());
        response.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * 헤더 검사 엔드포인트
     * Authorization 헤더가 제대로 전달되는지 확인
     */
    @GetMapping("/headers")
    public Mono<ResponseEntity<Map<String, Object>>> checkHeaders(
            @RequestHeader Map<String, String> headers,
            @AuthenticationPrincipal Jwt jwt) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Headers checked successfully");
        response.put("headers", headers);
        response.put("hasAuthorization", headers.containsKey("authorization"));
        response.put("hasXGateway", headers.containsKey("x-gateway"));
        
        if (jwt != null) {
            response.put("jwtPresent", true);
            response.put("subject", jwt.getSubject());
        } else {
            response.put("jwtPresent", false);
        }
        
        response.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(ResponseEntity.ok(response));
    }
}

