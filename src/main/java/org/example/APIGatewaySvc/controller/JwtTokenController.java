package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.APIGatewaySvc.util.JwtTestUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * JWT 토큰 생성 컨트롤러 (테스트용)
 * 로컬 개발 및 테스트 환경에서만 활성화됨
 * 
 * 주의: 이 컨트롤러는 오직 개발/테스트 목적으로만 사용되어야 하며,
 * 운영 환경에서는 비활성화되어야 합니다.
 */
@RestController
@RequestMapping("/public/jwt")
@Tag(name = "JWT Token Generator", description = "테스트용 JWT 토큰 생성 API (개발환경 전용)")
@ConditionalOnProperty(name = "jwt.test-mode", havingValue = "true")
public class JwtTokenController {

    @GetMapping("/tokens")
    @Operation(
        summary = "테스트용 JWT 토큰 생성기", 
        description = """
            **개발/테스트를 위한 JWT 토큰 생성 엔드포인트**
            
            **목적**: 실제 Auth0 없이 JWT 토큰 인증 로직을 테스트
            
            **접근**: 인증 불필요 (공개 엔드포인트)
            
            **제공 토큰 유형**:
            - **valid**: 정상적인 JWT 토큰 (일반 사용자 권한)
            - **admin**: 관리자 권한을 가진 JWT 토큰 
            - **readonly**: 읽기 권한만 있는 JWT 토큰
            - **expired**: 만료된 JWT 토큰 (401 에러 테스트용)
            - **invalid_audience**: 잘못된 오디언스 (401 에러 테스트용)
            
            **사용 방법**:
            1. 이 엔드포인트를 호출하여 토큰 획득
            2. 원하는 시나리오의 토큰을 복사
            3. 다른 API 호출 시 Authorization 헤더에 `Bearer {token}` 형식으로 사용
            
            ⚠️ **중요 주의사항**:
            - 오직 개발/테스트 목적으로만 사용
            - 운영 환경에서는 자동 비활성화됨
            - 실제 운영에서는 Auth0에서 발급한 토큰 사용 필수
            
            **활용 예시**:
            ```bash
            # 1. 토큰 획득
            curl http://localhost:8080/public/jwt/tokens
            
            # 2. 토큰을 사용한 API 호출
            curl -H "Authorization: Bearer {valid-token}" \
                 http://localhost:8080/test/protected
            ```
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "테스트용 JWT 토큰들이 성공적으로 생성되었습니다")
    })
    public Mono<ResponseEntity<Map<String, Object>>> getTestTokens() {
        Map<String, String> tokens = JwtTestUtil.getTestTokens();
        
        Map<String, Object> response = Map.of(
            "message", "테스트용 JWT 토큰들",
            "warning", "⚠️ 이 토큰들은 오직 개발/테스트 목적으로만 사용하세요!",
            "tokens", tokens,
            "usage", Map.of(
                "valid", "정상적인 JWT 토큰 (일반 사용자)",
                "admin", "관리자 권한을 가진 JWT 토큰",
                "readonly", "읽기 권한만 있는 JWT 토큰", 
                "expired", "만료된 JWT 토큰 (401 에러 테스트용)",
                "invalid_audience", "잘못된 오디언스 JWT 토큰 (401 에러 테스트용)"
            ),
            "instructions", List.of(
                "1. 원하는 토큰을 복사합니다",
                "2. Postman/Swagger에서 Authorization 헤더에 'Bearer {token}' 형식으로 추가합니다",
                "3. 보호된 엔드포인트에 요청을 보냅니다"
            )
        );
        
        return Mono.just(ResponseEntity.ok(response));
    }

    @PostMapping("/generate")
    @Operation(
        summary = "커스텀 JWT 토큰 생성", 
        description = "지정한 사용자 ID와 권한으로 JWT 토큰을 생성합니다."
    )
    public Mono<ResponseEntity<Map<String, Object>>> generateCustomToken(
            @RequestBody Map<String, Object> request) {
        
        String userId = (String) request.getOrDefault("userId", "custom-user");
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) request.getOrDefault("permissions", 
            List.of("read:users", "write:users"));
        
        String token = JwtTestUtil.createTestToken(userId, permissions);
        
        Map<String, Object> response = Map.of(
            "token", token,
            "userId", userId,
            "permissions", permissions,
            "expiresIn", "1시간",
            "warning", "⚠️ 이 토큰은 오직 개발/테스트 목적으로만 사용하세요!"
        );
        
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/decode/{token}")
    @Operation(
        summary = "JWT 토큰 디코딩", 
        description = "JWT 토큰의 내용을 디코딩하여 표시합니다."
    )
    public Mono<ResponseEntity<Map<String, Object>>> decodeToken(
            @PathVariable String token) {
        
        try {
            // 토큰 정보 출력 (로그용)
            JwtTestUtil.printTokenInfo(token);
            
            Map<String, Object> response = Map.of(
                "message", "토큰 정보가 서버 로그에 출력되었습니다",
                "token", token.substring(0, Math.min(50, token.length())) + "...",
                "note", "전체 토큰 정보는 서버 콘솔/로그를 확인하세요"
            );
            
            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                "error", "토큰 디코딩 실패",
                "message", e.getMessage()
            );
            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
        }
    }
}