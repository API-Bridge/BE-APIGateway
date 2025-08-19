package org.example.APIGatewaySvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/**
 * Router Function 기반 Auth 엔드포인트 설정
 * Spring Cloud Gateway에서 Controller가 작동하지 않는 문제 해결
 */
@Configuration
public class AuthRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> authRoutes() {
        return RouterFunctions
            .route(GET("/auth/test"), request -> {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "OK");
                response.put("message", "RouterFunction Auth endpoint is working");
                response.put("timestamp", java.time.Instant.now().toString());
                
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(response));
            })
            .andRoute(GET("/auth/token-info"), request -> {
                return ReactiveSecurityContextHolder.getContext()
                    .flatMap(securityContext -> {
                        try {
                            org.springframework.security.core.Authentication authentication = securityContext.getAuthentication();
                            Map<String, Object> tokenInfo = new HashMap<>();
                            
                            if (authentication != null) {
                                tokenInfo.put("authenticated", true);
                                tokenInfo.put("authenticationType", authentication.getClass().getSimpleName());
                                tokenInfo.put("principal", authentication.getPrincipal().getClass().getSimpleName());
                                tokenInfo.put("authorities", authentication.getAuthorities());
                                
                                // OAuth2AuthenticationToken인 경우 (OAuth2 Login 사용 시)
                                if (authentication instanceof OAuth2AuthenticationToken) {
                                    OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
                                    tokenInfo.put("registrationId", oauth2Token.getAuthorizedClientRegistrationId());
                                    
                                    // OidcUser에서 ID 토큰 정보 추출
                                    if (oauth2Token.getPrincipal() instanceof OidcUser) {
                                        OidcUser oidcUser = (OidcUser) oauth2Token.getPrincipal();
                                        tokenInfo.put("idTokenValue", oidcUser.getIdToken().getTokenValue());
                                        tokenInfo.put("idTokenClaims", oidcUser.getIdToken().getClaims());
                                        tokenInfo.put("userAttributes", oidcUser.getAttributes());
                                    }
                                }
                                
                                // JWT Authentication인 경우
                                if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt) {
                                    org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal();
                                    tokenInfo.put("jwtTokenValue", jwt.getTokenValue());
                                    tokenInfo.put("jwtClaims", jwt.getClaims());
                                    tokenInfo.put("jwtHeaders", jwt.getHeaders());
                                }
                            } else {
                                tokenInfo.put("authenticated", false);
                                tokenInfo.put("message", "인증 정보가 없습니다");
                            }
                            
                            return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(tokenInfo));
                                
                        } catch (Exception e) {
                            Map<String, Object> errorInfo = new HashMap<>();
                            errorInfo.put("authenticated", false);
                            errorInfo.put("error", "토큰 정보 조회 실패");
                            errorInfo.put("message", e.getMessage());
                            
                            return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(errorInfo));
                        }
                    })
                    .onErrorResume(error -> {
                        Map<String, Object> errorInfo = new HashMap<>();
                        errorInfo.put("authenticated", false);
                        errorInfo.put("error", "Security Context 조회 실패");
                        errorInfo.put("message", error.getMessage());
                        
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(errorInfo));
                    });
            })
            .andRoute(GET("/auth/user-info"), request -> {
                return ReactiveSecurityContextHolder.getContext()
                    .flatMap(securityContext -> {
                        org.springframework.security.core.Authentication authentication = securityContext.getAuthentication();
                        Map<String, Object> userInfo = new HashMap<>();
                        
                        if (authentication != null && authentication.isAuthenticated()) {
                            userInfo.put("authenticated", true);
                            
                            // OAuth2AuthenticationToken인 경우 (OAuth2 Login)
                            if (authentication instanceof OAuth2AuthenticationToken) {
                                OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
                                
                                if (oauth2Token.getPrincipal() instanceof OidcUser) {
                                    OidcUser oidcUser = (OidcUser) oauth2Token.getPrincipal();
                                    userInfo.put("userId", oidcUser.getSubject());
                                    userInfo.put("email", oidcUser.getEmail());
                                    userInfo.put("name", oidcUser.getFullName());
                                    userInfo.put("emailVerified", oidcUser.getEmailVerified());
                                    userInfo.put("tokenType", "OIDC");
                                } else {
                                    // OAuth2User인 경우
                                    org.springframework.security.oauth2.core.user.OAuth2User oauth2User = 
                                        (org.springframework.security.oauth2.core.user.OAuth2User) oauth2Token.getPrincipal();
                                    userInfo.put("userId", oauth2User.getAttribute("sub"));
                                    userInfo.put("email", oauth2User.getAttribute("email"));
                                    userInfo.put("name", oauth2User.getAttribute("name"));
                                    userInfo.put("tokenType", "OAuth2");
                                }
                                
                                userInfo.put("authorities", authentication.getAuthorities());
                                return ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(BodyInserters.fromValue(userInfo));
                            }
                            
                            // JWT 토큰인 경우
                            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt) {
                                org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal();
                                userInfo.put("userId", jwt.getClaimAsString("sub"));
                                userInfo.put("email", jwt.getClaimAsString("email"));
                                userInfo.put("name", jwt.getClaimAsString("name"));
                                userInfo.put("emailVerified", jwt.getClaimAsBoolean("email_verified"));
                                userInfo.put("authorities", authentication.getAuthorities());
                                userInfo.put("tokenType", "JWT");
                                return ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(BodyInserters.fromValue(userInfo));
                            }
                        }
                        
                        // 인증되지 않은 경우
                        userInfo.put("authenticated", false);
                        userInfo.put("message", "인증되지 않은 사용자");
                        userInfo.put("loginUrl", "/auth/login");
                        userInfo.put("instruction", "OAuth2 로그인 또는 JWT 토큰이 필요합니다");
                        
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(userInfo));
                    })
                    .onErrorResume(error -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("authenticated", false);
                        response.put("error", "인증 오류");
                        response.put("message", error.getMessage());
                        
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(response));
                    });
            });
    }
}