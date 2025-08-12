package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API Gateway 테스트용 컨트롤러
 * Swagger UI 및 Postman 테스트를 위한 Mock 엔드포인트 제공
 * 
 * 주요 기능:
 * - JWT 토큰 검증 테스트
 * - Rate Limiting 테스트  
 * - Gateway 필터 동작 테스트
 * - 에러 핸들링 테스트
 */
@RestController
@RequestMapping("/test")
@Tag(name = "Test Controller", description = "API Gateway 기능 테스트용 엔드포인트")
public class TestController {


    @GetMapping("/protected")
    @Operation(
        summary = "JWT 토큰 인증 테스트", 
        description = """
            **JWT 토큰 검증 테스트 엔드포인트**
            
            **목적**: Auth0 JWT 토큰 검증 로직이 올바르게 동작하는지 확인
            
            **인증 요구사항**:
            - Authorization 헤더: `Bearer {your-jwt-token}`
            - 유효한 Auth0 JWT 토큰 필요
            - Audience와 Issuer 검증 통과
            
            **테스트 방법**:
            1. `/public/jwt/tokens`에서 테스트 토큰 획득
            2. Authorization 헤더에 `Bearer {token}` 형식으로 설정
            3. 이 엔드포인트에 GET 요청 전송
            
            **성공 응답**:
            - message: 인증 성공 메시지
            - userId: JWT 토큰의 subject (사용자 ID)
            - timestamp: 요청 처리 시간
            - requestId: 요청 추적 ID
            
            **실패 케이스**:
            - 토큰 누락: 401 Unauthorized
            - 토큰 만료: 401 Unauthorized  
            - 잘못된 서명: 401 Unauthorized
            - 잘못된 Audience: 401 Unauthorized
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
@ApiResponse(responseCode = "200", description = "JWT 토큰 인증이 성공했습니다"),
        @ApiResponse(responseCode = "401", description = "JWT 토큰 인증이 실패했습니다 (토큰 누락, 만료, 또는 유효하지 않음)")
    })
    public Mono<ResponseEntity<Map<String, Object>>> protectedEndpoint(
            @Parameter(hidden = true) ServerWebExchange exchange) {
        
        return exchange.getPrincipal()
            .cast(JwtAuthenticationToken.class)
            .map(jwt -> {
                Map<String, Object> response = Map.of(
                    "message", "Authentication successful",
                    "userId", jwt.getToken().getSubject(),
                    "timestamp", LocalDateTime.now(),
                    "requestId", exchange.getResponse().getHeaders().getFirst("X-Request-ID")
                );
                return ResponseEntity.ok(response);
            })
            .switchIfEmpty(Mono.just(ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"))));
    }

    @GetMapping("/rate-limit-test")
    @Operation(
        summary = "Rate Limiting 테스트", 
        description = """
            **Rate Limiting 동작 확인 엔드포인트**
            
            **목적**: Redis 기반 Rate Limiting이 정상 동작하는지 테스트
            
            **인증**: JWT Bearer 토큰 필요
            
            **Rate Limit 설정**:
            - 기본: 초당 10개 요청, 버스트 20개
            - 사용자별로 개별 제한 적용
            - Redis를 통한 분산 Rate Limiting
            
            **테스트 방법**:
            1. 유효한 JWT 토큰으로 인증 설정
            2. 이 엔드포인트를 빠르게 연속 호출 (25회 이상)
            3. 일정 횟수 초과 시 429 에러 확인
            
            **응답 헤더 (Rate Limit 정보)**:
            - X-RateLimit-Remaining: 남은 요청 수
            - X-RateLimit-Burst-Capacity: 버스트 용량
            - X-RateLimit-Replenish-Rate: 초당 보충 속도
            
            **활용 사례**:
            - API 남용 방지
            - 서버 리소스 보호
            - 공정한 API 사용량 분배
            
            **주의사항**: Redis 서버가 실행중이어야 정상 동작
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
@ApiResponse(responseCode = "200", description = "요청이 Rate Limit 범위 내에서 성공적으로 처리되었습니다"),
        @ApiResponse(responseCode = "429", description = "Rate Limit 초과 - 잠시 후 다시 시도해주세요")
    })
    public Mono<ResponseEntity<Map<String, Object>>> rateLimitTest() {
        Map<String, Object> response = Map.of(
            "message", "Rate limit test successful",
            "timestamp", LocalDateTime.now(),
            "tip", "빠르게 여러 번 호출하여 429 에러를 확인해보세요"
        );
        return Mono.just(ResponseEntity.ok(response));
    }

    @PostMapping("/echo")
    @Operation(
        summary = "Echo Test", 
        description = "요청 데이터를 그대로 반환하는 테스트 엔드포인트",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public Mono<ResponseEntity<Map<String, Object>>> echoTest(
            @RequestBody Map<String, Object> requestBody,
            @Parameter(hidden = true) ServerWebExchange exchange) {
        
        Map<String, Object> response = Map.of(
            "echo", requestBody,
            "timestamp", LocalDateTime.now(),
            "headers", Map.of(
                "requestId", exchange.getResponse().getHeaders().getFirst("X-Request-ID"),
                "contentType", exchange.getRequest().getHeaders().getContentType()
            )
        );
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/error-test")
    @Operation(
        summary = "Error Test", 
        description = "에러 핸들링 테스트용 엔드포인트",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "500", description = "의도적 서버 에러")
    })
    public Mono<ResponseEntity<Map<String, Object>>> errorTest() {
        return Mono.error(new RuntimeException("Test error for error handling"));
    }

}