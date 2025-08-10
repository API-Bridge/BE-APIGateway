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

    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "API Gateway 상태 확인 (인증 불필요)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "정상 응답")
    })
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        Map<String, Object> response = Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now(),
            "service", "API Gateway",
            "version", "1.0.0"
        );
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/protected")
    @Operation(
        summary = "Protected Endpoint", 
        description = "JWT 토큰이 필요한 보호된 엔드포인트",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "인증 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
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
        summary = "Rate Limit Test", 
        description = "Rate Limiting 동작 테스트용 엔드포인트",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "요청 성공"),
        @ApiResponse(responseCode = "429", description = "Rate Limit 초과")
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

    @GetMapping("/user-info")
    @Operation(
        summary = "User Info", 
        description = "JWT 토큰에서 사용자 정보 추출",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public Mono<ResponseEntity<Map<String, Object>>> userInfo(
            @Parameter(hidden = true) ServerWebExchange exchange) {
        
        return exchange.getPrincipal()
            .cast(JwtAuthenticationToken.class)
            .map(jwt -> {
                Map<String, Object> response = Map.of(
                    "userId", jwt.getToken().getSubject(),
                    "audience", jwt.getToken().getAudience(),
                    "issuer", jwt.getToken().getIssuer(),
                    "issuedAt", jwt.getToken().getIssuedAt(),
                    "expiresAt", jwt.getToken().getExpiresAt(),
                    "authorities", jwt.getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .toList()
                );
                return ResponseEntity.ok(response);
            });
    }
}