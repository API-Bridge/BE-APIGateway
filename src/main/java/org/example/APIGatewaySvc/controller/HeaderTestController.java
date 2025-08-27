package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT User Header Filter 테스트용 컨트롤러
 * 
 * JwtUserHeaderFilter가 JWT 토큰에서 추출한 사용자 정보 헤더들을 
 * 제대로 생성했는지 확인하는 테스트 엔드포인트입니다.
 */
@RestController
@RequestMapping("/test")
@Tag(name = "Header Test", description = "JWT User Header Filter 테스트 API")
public class HeaderTestController {

    @GetMapping("/jwt-headers")
    @Operation(
        summary = "JWT 헤더 변환 테스트", 
        description = """
            **JWT User Header Filter 테스트 엔드포인트**
            
            이 엔드포인트는 JWT 토큰이 올바르게 사용자 정보 헤더로 변환되었는지 확인합니다.
            
            **사용 방법**:
            1. Authorization 헤더에 유효한 JWT 토큰을 포함하여 요청
            2. JwtUserHeaderFilter가 자동으로 다음 헤더들을 생성
               - X-User-Id: 사용자 ID
               - X-User-Email: 사용자 이메일  
               - X-User-Role: 주 역할
               - X-User-Roles: 모든 역할
               - X-User-Permissions: 모든 권한
            
            **예시 요청**:
            ```bash
            curl -H "Authorization: Bearer <JWT토큰>" \\
                 http://localhost:8080/test/jwt-headers
            ```
            
            **응답 예시**:
            ```json
            {
              "message": "JWT 헤더 변환 성공",
              "headers": {
                "X-User-Id": "auth0|12345",
                "X-User-Email": "user@example.com",
                "X-User-Role": "user",
                "X-User-Roles": "admin,user", 
                "X-User-Permissions": "read:users,write:users"
              }
            }
            ```
            
            ⚠️ **주의**: 이 엔드포인트는 테스트 목적이므로 운영환경에서는 제거하세요.
            """
    )
    @ApiResponse(responseCode = "200", description = "JWT 헤더 정보 조회 성공")
    public Mono<ResponseEntity<Map<String, Object>>> testJwtHeaders(
            ServerWebExchange exchange) {
        
        Map<String, Object> response = new HashMap<>();
        Map<String, String> extractedHeaders = new HashMap<>();
        
        // JwtUserHeaderFilter가 생성한 헤더들을 확인
        org.springframework.http.HttpHeaders headers = exchange.getRequest().getHeaders();
        
        // X-User-Id 헤더 확인
        String userId = headers.getFirst("X-User-Id");
        if (userId != null) {
            extractedHeaders.put("X-User-Id", userId);
        }
        
        // X-User-Email 헤더 확인
        String userEmail = headers.getFirst("X-User-Email");
        if (userEmail != null) {
            extractedHeaders.put("X-User-Email", userEmail);
        }
        
        // X-User-Role 헤더 확인
        String userRole = headers.getFirst("X-User-Role");
        if (userRole != null) {
            extractedHeaders.put("X-User-Role", userRole);
        }
        
        // X-User-Roles 헤더 확인
        String userRoles = headers.getFirst("X-User-Roles");
        if (userRoles != null) {
            extractedHeaders.put("X-User-Roles", userRoles);
        }
        
        // X-User-Permissions 헤더 확인
        String userPermissions = headers.getFirst("X-User-Permissions");
        if (userPermissions != null) {
            extractedHeaders.put("X-User-Permissions", userPermissions);
        }
        
        // Authorization 헤더도 확인 (JWT 토큰)
        String authHeader = headers.getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String tokenPreview = authHeader.substring(0, Math.min(50, authHeader.length())) + "...";
            extractedHeaders.put("Authorization", tokenPreview);
        }
        
        // 응답 생성
        if (extractedHeaders.isEmpty()) {
            response.put("message", "JWT 헤더가 감지되지 않았습니다. JWT 토큰을 확인하세요.");
            response.put("note", "Authorization: Bearer <token> 헤더가 필요합니다.");
        } else {
            response.put("message", "JWT 헤더 변환 성공");
            response.put("note", "JwtUserHeaderFilter가 JWT 토큰을 사용자 정보 헤더로 변환했습니다.");
        }
        
        response.put("headers", extractedHeaders);
        response.put("timestamp", java.time.Instant.now().toString());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/public-test")
    @Operation(
        summary = "공개 테스트 (JWT 불필요)", 
        description = "JWT 토큰 없이도 접근 가능한 테스트 엔드포인트"
    )
    public Mono<ResponseEntity<Map<String, Object>>> publicTest() {
        Map<String, Object> response = Map.of(
            "message", "공개 엔드포인트 테스트 성공",
            "note", "이 엔드포인트는 JWT 토큰 없이도 접근 가능합니다",
            "timestamp", java.time.Instant.now().toString()
        );
        
        return Mono.just(ResponseEntity.ok(response));
    }
}