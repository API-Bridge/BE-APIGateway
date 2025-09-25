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
            // Authorization 헤더에서 직접 JWT 토큰 추출
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Authorization 헤더가 없음 - 헤더 추가 생략");
                return chain.filter(exchange);
            }
            
            String token = authHeader.substring(7).trim();
            
            try {
                // JWT 토큰을 직접 파싱해서 사용자 정보 추출
                String[] parts = token.split("\\.");
                if (parts.length != 3) {
                    log.debug("유효하지 않은 JWT 형식 - 헤더 추가 생략");
                    return chain.filter(exchange);
                }
                
                // JWT payload 디코딩
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                
                // JSON에서 사용자 정보 추출
                String userId = extractFromJson(payload, "sub");
                String userEmail = extractFromJson(payload, "email");
                
                log.debug("JWT에서 추출한 사용자 정보 - userId: {}, email: {}", userId, userEmail);
                
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
                
                // 수정된 요청으로 계속 진행
                ServerHttpRequest modifiedRequest = requestBuilder.build();
                ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();
                
                return chain.filter(modifiedExchange);
                
            } catch (Exception e) {
                log.error("JWT 사용자 헤더 생성 중 오류 발생: {}", e.getMessage(), e);
                // 오류가 발생해도 요청은 계속 진행
                return chain.filter(exchange);
            }
        };
    }
    
    /**
     * JSON 문자열에서 특정 필드 값을 추출합니다
     */
    private String extractFromJson(String json, String field) {
        try {
            String searchPattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern);
            java.util.regex.Matcher matcher = pattern.matcher(json);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            return null;
        } catch (Exception e) {
            log.error("JSON에서 필드 '{}' 추출 실패: {}", field, e.getMessage());
            return null;
        }
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