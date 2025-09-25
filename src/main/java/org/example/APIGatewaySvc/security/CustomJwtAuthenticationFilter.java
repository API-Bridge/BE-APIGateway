package org.example.APIGatewaySvc.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
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

        // Actuator 경로는 완전히 건너뛰기 (로그 없이)
        if (path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        // 이미 이 필터를 통과한 요청인지 확인 (무한 루프 방지)
        if (exchange.getAttribute("CustomJwtFilter.processed") != null) {
            return chain.filter(exchange);
        }
        
        // 필터 처리 표시
        exchange.getAttributes().put("CustomJwtFilter.processed", true);

        System.out.println("=== CustomJwtAuthenticationFilter 시작 ===");
        System.out.println("경로: " + path);
        System.out.println("메서드: " + exchange.getRequest().getMethod());

        // 공개 경로는 JWT 검증하지 않음
        if (isPublicPath(path)) {
            System.out.println("공개 경로이므로 JWT 검증 건너뛰기: " + path);
            return chain.filter(exchange);
        }

        // 이미 JWT 검증이 완료된 요청인지 확인
        String jwtValidated = exchange.getRequest().getHeaders().getFirst("X-JWT-Validated");
        System.out.println("X-JWT-Validated 헤더: " + jwtValidated);
        if ("true".equals(jwtValidated)) {
            System.out.println("JWT 검증 이미 완료됨, 건너뛰기: " + path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return handleUnauthorized(exchange, "Authorization 헤더가 없거나 Bearer 형식이 아닙니다");
        }

        String token = authHeader.substring(7).trim().replaceAll("\\s+", ""); // "Bearer " 제거 및 공백 정제

        return ReactiveSecurityContextHolder.getContext()
                .cast(SecurityContext.class)
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .flatMap(existingAuth -> {
                    System.out.println("기존 인증 정보 발견, JWT 검증 건너뛰기: " + path);
                    return chain.filter(exchange);
                })
                .switchIfEmpty(
                    jwtValidator.validateToken(token)
                            .flatMap(result -> {
                                if (result.isValid()) {
                                    // 인증 성공: Security Context에 인증 정보 설정
                                    Authentication authentication = createAuthentication(result.getPayload(), token);

                                    // JWT 검증이 완료되었음을 표시하는 속성 추가
                                    ServerWebExchange modifiedExchange = exchange.mutate()
                                            .request(exchange.getRequest().mutate()
                                                    .header("X-JWT-Validated", "true")
                                                    .build())
                                            .build();

                                    System.out.println("인증 성공 - Security Context에 인증 정보 설정: " + authentication.getName());
                                    System.out.println("인증 권한: " + authentication.getAuthorities());

                                    return chain.filter(modifiedExchange)
                                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                                } else {
                                    // 인증 실패
                                    return handleUnauthorized(exchange, result.getErrorMessage());
                                }
                            })
                            .onErrorResume(error -> {
                                // JWT 검증 오류만 401로 처리, 나머지는 백엔드로 전달
                                String errorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

                                // HTTP 상태 관련 오류는 JWT 검증과 무관하므로 그대로 전파
                                if (errorMessage.contains("METHOD_NOT_ALLOWED") ||
                                    errorMessage.contains("405") ||
                                    errorMessage.contains("404") ||
                                    errorMessage.contains("409") ||
                                    errorMessage.contains("NOT_FOUND") ||
                                    errorMessage.contains("CONFLICT") ||
                                    error instanceof org.springframework.web.server.MethodNotAllowedException ||
                                    error instanceof org.springframework.web.server.ResponseStatusException) {
                                    System.out.println("HTTP 상태 오류 - 백엔드로 전달: " + errorMessage);
                                    return Mono.error(error); // 오류를 그대로 전파
                                }

                                // 실제 JWT 검증 오류만 401로 처리
                                if (errorMessage.contains("JWT") ||
                                    errorMessage.contains("token") ||
                                    errorMessage.contains("JWKS") ||
                                    errorMessage.contains("signature") ||
                                    errorMessage.contains("expired") ||
                                    errorMessage.contains("invalid")) {
                                    System.out.println("JWT 검증 오류: " + errorMessage);
                                    return handleUnauthorized(exchange, "JWT 검증 실패: " + errorMessage);
                                }

                                // 기타 오류는 백엔드로 전달
                                System.out.println("기타 오류 - 백엔드로 전달: " + errorMessage);
                                return Mono.error(error); // 오류를 그대로 전파
                            })
                );
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
               path.equals("/gateway/users/api/auth/login") ||
               path.equals("/gateway/users/api/auth/register") ||
               path.equals("/") ||
               path.equals("/favicon.ico");
    }

    /**
     * JWT payload로부터 JwtAuthenticationToken 생성 (실제 토큰 포함)
     */
    private Authentication createAuthentication(JsonNode payload, String actualToken) {
        String subject = payload.get("sub").asText();
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

//        // roles 클레임에서 권한 추출 - 관리자 확인 로직 변경으로 사용 X
//        JsonNode rolesNode = payload.get("role");
//        if (rolesNode != null && rolesNode.isArray()) {
//            rolesNode.forEach(role -> {
//                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.asText().toUpperCase()));
//            });
//        }

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

        // JWT 객체 생성 (실제 토큰 값 사용)
        Jwt jwt = Jwt.withTokenValue(actualToken)
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