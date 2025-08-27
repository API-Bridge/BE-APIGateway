package org.example.APIGatewaySvc.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 커스텀 JWT 인증 필터
 * EnhancedJwtValidator를 사용하여 JWT를 검증하고
 * Spring Security 컨텍스트에 인증 정보를 설정합니다
 */
@Component
public class CustomJwtAuthenticationFilter implements WebFilter {
    
    private final EnhancedJwtValidator jwtValidator;
    
    public CustomJwtAuthenticationFilter(EnhancedJwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 공개 경로는 JWT 검증하지 않음
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return handleUnauthorized(exchange, "Authorization 헤더가 없거나 Bearer 형식이 아닙니다");
        }
        
        String token = authHeader.substring(7).trim().replaceAll("\\s+", ""); // "Bearer " 제거 및 공백 정제
        
        return jwtValidator.validateToken(token)
                .flatMap(result -> {
                    if (result.isValid()) {
                        // 인증 성공: Security Context에 인증 정보 설정
                        Authentication authentication = createAuthentication(result.getPayload());
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                    } else {
                        // 인증 실패
                        return handleUnauthorized(exchange, result.getErrorMessage());
                    }
                })
                .onErrorResume(error -> {
                    System.out.println("JWT 검증 중 예상치 못한 오류: " + error.getMessage());
                    return handleUnauthorized(exchange, "JWT 검증 중 오류 발생: " + error.getMessage());
                });
    }
    
    /**
     * 공개 경로 확인
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/auth/") ||
               path.startsWith("/oauth2/") ||
               path.startsWith("/login/") ||
               path.startsWith("/public/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.equals("/") ||
               path.equals("/favicon.ico");
    }
    
    /**
     * JWT payload로부터 JwtAuthenticationToken 생성
     */
    private Authentication createAuthentication(JsonNode payload) {
        String subject = payload.get("sub").asText();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        
        // roles 클레임에서 권한 추출
        JsonNode rolesNode = payload.get("role");
        if (rolesNode != null && rolesNode.isArray()) {
            rolesNode.forEach(role -> {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.asText().toUpperCase()));
            });
        }
        
        // permissions 클레임에서 권한 추출
        JsonNode permissionsNode = payload.get("permissions");
        if (permissionsNode != null && permissionsNode.isArray()) {
            permissionsNode.forEach(permission -> {
                authorities.add(new SimpleGrantedAuthority(permission.asText()));
            });
        }
        
        // 기본 USER 권한 추가 (권한이 없는 경우)
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        
        // JWT 클레임을 Map으로 변환
        Map<String, Object> claims = new HashMap<>();
        payload.fields().forEachRemaining(field -> {
            String key = field.getKey();
            JsonNode value = field.getValue();
            
            // timestamp 클레임들은 Instant로 변환
            if (("exp".equals(key) || "iat".equals(key) || "nbf".equals(key)) && value.isNumber()) {
                claims.put(key, java.time.Instant.ofEpochSecond(value.asLong()));
            } else if (value.isTextual()) {
                claims.put(key, value.asText());
            } else if (value.isNumber()) {
                claims.put(key, value.asLong());
            } else if (value.isBoolean()) {
                claims.put(key, value.asBoolean());
            } else if (value.isArray()) {
                List<String> list = new ArrayList<>();
                value.forEach(item -> list.add(item.asText()));
                claims.put(key, list);
            } else {
                claims.put(key, value.toString());
            }
        });
        
        // JWT 객체 생성
        Jwt jwt = Jwt.withTokenValue("dummy-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .claims(claimsMap -> claimsMap.putAll(claims))
                .build();
        
        return new JwtAuthenticationToken(jwt, authorities);
    }
    
    /**
     * 인증 실패 응답 처리
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        System.out.println("JWT 인증 실패: " + message);
        
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        String errorResponse = String.format(
            """
            {
                "error": "Unauthorized",
                "message": "%s",
                "status": 401,
                "timestamp": "%s",
                "path": "%s"
            }
            """,
            message,
            java.time.Instant.now().toString(),
            exchange.getRequest().getPath().value()
        );
        
        byte[] bytes = errorResponse.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}