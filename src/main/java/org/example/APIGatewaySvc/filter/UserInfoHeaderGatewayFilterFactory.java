package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 사용자 정보 헤더 추가 필터
 * 
 * API Gateway에서 JWT 토큰을 검증한 후, 다운스트림 서비스가 
 * 토큰 재검증 없이 사용할 수 있도록 사용자 정보를 헤더로 전달합니다.
 * 
 * 전달하는 헤더:
 * - X-User-Id: JWT의 sub 클레임 (사용자 식별자)
 * - X-User-Email: JWT의 email 클레임
 * - X-User-Authorities: JWT의 permissions 클레임 (쉼표로 구분)
 * - X-User-Roles: JWT의 roles 클레임 (쉼표로 구분)
 * - X-Gateway-Verified: 게이트웨이 검증 완료 표시
 */
@Slf4j
@Component
public class UserInfoHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<UserInfoHeaderGatewayFilterFactory.Config> {

    public UserInfoHeaderGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return ReactiveSecurityContextHolder.getContext()
                .cast(org.springframework.security.core.context.SecurityContext.class)
                .map(securityContext -> securityContext.getAuthentication())
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    try {
                        // JWT 토큰에서 사용자 정보 추출
                        Jwt jwt = jwtAuth.getToken();
                        
                        // ServerHttpRequest 변경을 위한 뮤테이터 생성
                        var requestMutator = exchange.getRequest().mutate();
                        
                        // 사용자 ID (sub 클레임)
                        String userId = jwt.getSubject();
                        if (userId != null) {
                            requestMutator.header("X-User-Id", userId);
                        }
                        
                        // 사용자 이메일
                        String email = jwt.getClaimAsString("email");
                        if (email != null) {
                            requestMutator.header("X-User-Email", email);
                        }
                        
                        // 권한 정보 (permissions 클레임)
                        Collection<String> permissions = jwt.getClaimAsStringList("permissions");
                        if (permissions != null && !permissions.isEmpty()) {
                            String authoritiesHeader = String.join(",", permissions);
                            requestMutator.header("X-User-Authorities", authoritiesHeader);
                        }
                        
                        // 역할 정보 (roles 클레임)
                        Collection<String> roles = jwt.getClaimAsStringList("roles");
                        if (roles != null && !roles.isEmpty()) {
                            String rolesHeader = String.join(",", roles);
                            requestMutator.header("X-User-Roles", rolesHeader);
                        }
                        
                        // Spring Security authorities도 함께 전달 (백업용)
                        String springAuthorities = jwtAuth.getAuthorities().stream()
                            .map(auth -> auth.getAuthority())
                            .collect(Collectors.joining(","));
                        if (!springAuthorities.isEmpty()) {
                            requestMutator.header("X-Spring-Authorities", springAuthorities);
                        }
                        
                        // 게이트웨이 검증 완료 표시
                        requestMutator
                            .header("X-Gateway-Verified", "true")
                            .header("X-Gateway-Verification-Time", String.valueOf(System.currentTimeMillis()));
                        
                        // 변경된 요청으로 교체
                        var modifiedExchange = exchange.mutate().request(requestMutator.build()).build();
                        
                        log.debug("사용자 정보 헤더 추가 완료 - UserId: {}, Email: {}, Authorities: {}, Roles: {}", 
                                 userId, email, permissions, roles);
                        
                        return chain.filter(modifiedExchange);
                        
                    } catch (Exception e) {
                        log.warn("사용자 정보 헤더 추가 중 오류 발생: {}", e.getMessage(), e);
                        return chain.filter(exchange);
                    }
                })
                .switchIfEmpty(
                    // 인증되지 않은 요청의 경우 그대로 전달
                    Mono.defer(() -> {
                        log.debug("인증되지 않은 요청 - 사용자 정보 헤더 추가 건너뜀");
                        return chain.filter(exchange);
                    })
                );
        };
    }

    /**
     * 필터 설정 클래스
     */
    public static class Config {
        // 현재는 설정 옵션 없음, 필요시 추가 가능
        private boolean includeTokenClaims = true;
        private boolean includeSpringAuthorities = true;
        
        public boolean isIncludeTokenClaims() {
            return includeTokenClaims;
        }
        
        public void setIncludeTokenClaims(boolean includeTokenClaims) {
            this.includeTokenClaims = includeTokenClaims;
        }
        
        public boolean isIncludeSpringAuthorities() {
            return includeSpringAuthorities;
        }
        
        public void setIncludeSpringAuthorities(boolean includeSpringAuthorities) {
            this.includeSpringAuthorities = includeSpringAuthorities;
        }
    }
}
