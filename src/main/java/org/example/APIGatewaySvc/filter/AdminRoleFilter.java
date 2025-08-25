package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.util.JwtRoleUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Auth0 JWT 기반 관리자 권한 체크 필터
 * 
 * API Gateway에서 관리자 전용 경로에 대한 접근을 제어합니다.
 * JWT 토큰의 역할 정보를 확인하여 admin 역할이 있는 경우만 통과시킵니다.
 */
@Component
@Slf4j
public class AdminRoleFilter extends AbstractGatewayFilterFactory<AdminRoleFilter.Config> {
    
    private final JwtRoleUtils jwtRoleUtils;
    
    public AdminRoleFilter(JwtRoleUtils jwtRoleUtils) {
        super(Config.class);
        this.jwtRoleUtils = jwtRoleUtils;
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // SecurityContext에서 Authentication 추출 시도
            return exchange.<Authentication>getPrincipal()
                .cast(Authentication.class)
                .flatMap(authentication -> {
                    // JWT 토큰 추출
                    Jwt jwt = jwtRoleUtils.extractJwtFromAuthentication(authentication);
                    
                    if (jwt == null) {
                        log.warn("관리자 권한 체크 실패 - JWT 토큰이 없음. Path: {}", 
                            exchange.getRequest().getPath());
                        return createUnauthorizedResponse(exchange, "JWT 토큰이 없습니다.");
                    }
                    
                    // JWT 토큰 내용 디버깅
                    log.debug("JWT 클레임 전체: {}", jwt.getClaims());
                    log.debug("JWT 서브젝트: {}", jwt.getSubject());
                    
                    // 관리자 역할 확인 전에 역할 정보 디버깅
                    var roles = jwtRoleUtils.extractRoles(jwt);
                    log.debug("추출된 역할 목록: {}", roles);
                    
                    // 관리자 역할 확인
                    if (!jwtRoleUtils.hasAdminRole(jwt)) {
                        String userEmail = jwtRoleUtils.extractEmail(jwt);
                        String userId = jwtRoleUtils.extractAuth0Id(jwt);
                        
                        log.warn("관리자 권한 없는 사용자의 접근 시도: {} ({}) - Path: {}, 역할: {}", 
                            userEmail, userId, exchange.getRequest().getPath(), roles);
                        
                        return createForbiddenResponse(exchange, config.getMessage());
                    }
                    
                    // 관리자 권한 확인 완료
                    String userEmail = jwtRoleUtils.extractEmail(jwt);
                    String userId = jwtRoleUtils.extractAuth0Id(jwt);
                    log.info("관리자 권한 확인 완료: {} ({}) - Path: {}", 
                        userEmail, userId, exchange.getRequest().getPath());
                    
                    // UserService가 필요로 하는 헤더 추가
                    var modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "admin")
                        .header("X-User-Email", userEmail)
                        .build();
                    
                    var modifiedExchange = exchange.mutate()
                        .request(modifiedRequest)
                        .build();
                    
                    // 다음 필터로 진행
                    return chain.filter(modifiedExchange);
                })
                .onErrorResume(error -> {
                    log.error("관리자 권한 체크 중 오류 발생: {}", error.getMessage(), error);
                    return createUnauthorizedResponse(exchange, "권한 확인 중 오류가 발생했습니다.");
                });
        };
    }
    
    /**
     * 401 Unauthorized 응답 생성
     */
    private Mono<Void> createUnauthorizedResponse(
            org.springframework.web.server.ServerWebExchange exchange, 
            String message) {
        
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        String errorResponse = String.format("""
            {
                "error": "Unauthorized",
                "message": "%s",
                "status": 401,
                "timestamp": "%s",
                "path": "%s"
            }
            """, 
            message,
            Instant.now().toString(),
            exchange.getRequest().getPath().value()
        );
        
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    
    /**
     * 403 Forbidden 응답 생성
     */
    private Mono<Void> createForbiddenResponse(
            org.springframework.web.server.ServerWebExchange exchange, 
            String message) {
        
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        
        String errorResponse = String.format("""
            {
                "error": "Forbidden",
                "message": "%s",
                "status": 403,
                "timestamp": "%s",
                "path": "%s"
            }
            """, 
            message,
            Instant.now().toString(),
            exchange.getRequest().getPath().value()
        );
        
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorResponse.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    
    /**
     * 필터 설정 클래스
     */
    public static class Config {
        private String message = "관리자 권한이 필요합니다.";
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}