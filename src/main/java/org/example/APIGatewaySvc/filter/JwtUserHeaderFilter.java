package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.util.JwtRoleUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 토큰에서 사용자 정보를 추출하여 HTTP 헤더로 변환하는 필터
 * 
 * API Gateway에서 JWT 검증 후 백엔드 서비스로 사용자 정보를 헤더로 전달합니다.
 * 백엔드 서비스는 JWT를 재검증하지 않고 헤더 정보만으로 사용자를 식별할 수 있습니다.
 * 
 * 전달되는 헤더:
 * - X-User-Id: Auth0 사용자 ID (subject)
 * - X-User-Email: 사용자 이메일
 * - X-User-Role: 주 역할 (첫 번째 권한 기반)
 * - X-User-Roles: 모든 역할 (쉼표 구분)
 * - X-User-Permissions: 모든 권한 (쉼표 구분)
 */
@Slf4j
@Component
public class JwtUserHeaderFilter extends AbstractGatewayFilterFactory<JwtUserHeaderFilter.Config> {

    private final JwtRoleUtils jwtRoleUtils;

    public JwtUserHeaderFilter(JwtRoleUtils jwtRoleUtils) {
        super(Config.class);
        this.jwtRoleUtils = jwtRoleUtils;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return ReactiveSecurityContextHolder.getContext()
                .cast(org.springframework.security.core.context.SecurityContext.class)
                .flatMap(securityContext -> {
                    Authentication authentication = securityContext.getAuthentication();
                    
                    if (authentication == null || !authentication.isAuthenticated()) {
                        log.debug("인증되지 않은 요청 - 헤더 추가 생략");
                        return chain.filter(exchange);
                    }
                    
                    // JWT 토큰 추출
                    Jwt jwt = jwtRoleUtils.extractJwtFromAuthentication(authentication);
                    if (jwt == null) {
                        log.debug("JWT 토큰이 없음 - 헤더 추가 생략");
                        return chain.filter(exchange);
                    }
                    
                    try {
                        // JWT에서 사용자 정보 추출
                        String userId = jwtRoleUtils.extractAuth0Id(jwt);
                        String userEmail = jwtRoleUtils.extractEmail(jwt);
                        List<String> permissions = jwtRoleUtils.extractPermissions(jwt);
                        
                        log.debug("JWT에서 추출한 사용자 정보 - userId: {}, email: {}, permissions: {}", 
                                userId, userEmail, permissions);
                        
                        // 요청에 사용자 정보 헤더 추가
                        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
                        
                        // 필수 헤더: X-User-Id (사용자 식별용)
                        if (userId != null && !userId.trim().isEmpty()) {
                            requestBuilder.header("X-User-Id", userId);
                            log.debug("X-User-Id 헤더 추가: {}", userId);
                        }
                        
                        // 선택적 헤더: X-User-Email
                        if (userEmail != null && !userEmail.trim().isEmpty()) {
                            requestBuilder.header("X-User-Email", userEmail);
                            log.debug("X-User-Email 헤더 추가: {}", userEmail);
                        }
                        
                        // 권한 관련 헤더
                        if (permissions != null && !permissions.isEmpty()) {
                            // X-User-Role: 첫 번째 권한을 주 역할로 설정
                            String primaryRole = extractPrimaryRole(permissions);
                            if (primaryRole != null) {
                                requestBuilder.header("X-User-Role", primaryRole);
                                log.debug("X-User-Role 헤더 추가: {}", primaryRole);
                            }
                            
                            // X-User-Roles: 모든 역할을 쉼표로 구분
                            String roles = extractRoles(permissions);
                            if (roles != null && !roles.trim().isEmpty()) {
                                requestBuilder.header("X-User-Roles", roles);
                                log.debug("X-User-Roles 헤더 추가: {}", roles);
                            }
                            
                            // X-User-Permissions: 모든 권한을 쉼표로 구분
                            String permissionsStr = String.join(",", permissions);
                            requestBuilder.header("X-User-Permissions", permissionsStr);
                            log.debug("X-User-Permissions 헤더 추가: {}", permissionsStr);
                        }
                        
                        // 수정된 요청으로 계속 진행
                        ServerHttpRequest modifiedRequest = requestBuilder.build();
                        ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();
                        
                        return chain.filter(modifiedExchange);
                        
                    } catch (Exception e) {
                        log.error("JWT 사용자 헤더 생성 중 오류 발생: {}", e.getMessage(), e);
                        // 오류가 발생해도 요청은 계속 진행
                        return chain.filter(exchange);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Security Context가 없음 - 헤더 추가 생략");
                    return chain.filter(exchange);
                }));
        };
    }
    
    /**
     * 권한 목록에서 주 역할을 추출합니다
     * read:users -> user, admin:users -> admin 등
     */
    private String extractPrimaryRole(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        
        // 관리자 권한이 있으면 admin 반환
        for (String permission : permissions) {
            if (permission.toLowerCase().contains("admin")) {
                return "admin";
            }
        }
        
        // 첫 번째 권한에서 역할 추출 (예: read:users -> user)
        String firstPermission = permissions.get(0);
        if (firstPermission.contains(":")) {
            String role = firstPermission.split(":")[1].replaceAll("s$", ""); // users -> user
            return role;
        }
        
        return "user"; // 기본값
    }
    
    /**
     * 권한 목록에서 모든 역할을 추출합니다
     */
    private String extractRoles(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        
        StringBuilder roles = new StringBuilder();
        boolean hasAdmin = false;
        boolean hasUser = false;
        
        // 관리자/사용자 역할 확인
        for (String permission : permissions) {
            if (permission.toLowerCase().contains("admin")) {
                hasAdmin = true;
            } else if (permission.contains(":")) {
                hasUser = true;
            }
        }
        
        // 역할 목록 생성
        if (hasAdmin) {
            roles.append("admin");
        }
        if (hasUser) {
            if (roles.length() > 0) {
                roles.append(",");
            }
            roles.append("user");
        }
        
        // 역할이 없으면 기본값
        if (roles.length() == 0) {
            return "user";
        }
        
        return roles.toString();
    }

    /**
     * 필터 설정 클래스
     */
    public static class Config {
        // 필요시 설정 옵션 추가 가능
        private boolean debugMode = false;
        
        public boolean isDebugMode() {
            return debugMode;
        }
        
        public void setDebugMode(boolean debugMode) {
            this.debugMode = debugMode;
        }
    }
}