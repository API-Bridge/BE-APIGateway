package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.example.APIGatewaySvc.service.GatewayLogService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auth0 OAuth2 로그인 처리를 위한 컨트롤러
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Auth0 OAuth2 인증 관련 API")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${auth0.issuerUri}")
    private String issuerUri;

    @Value("${auth0.client-id:}")
    private String clientId;

    @Value("${auth0.audience}")
    private String audience;

    @Value("${auth0.logout-redirect-uri:http://localhost:8080/auth/logout-success}")
    private String logoutRedirectUri;

    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    private final GatewayLogService gatewayLogService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public AuthController(ReactiveOAuth2AuthorizedClientService authorizedClientService,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) GatewayLogService gatewayLogService,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) ReactiveRedisTemplate<String, String> redisTemplate) {
        this.authorizedClientService = authorizedClientService;
        this.gatewayLogService = gatewayLogService;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * JWT 토큰을 블랙리스트에 추가하여 무효화
     */
    private Mono<Boolean> blacklistJwtToken(Jwt jwt) {
        if (redisTemplate == null || jwt == null) {
            log.debug("Redis template이 없거나 JWT가 null이므로 블랙리스트 처리를 건너뜁니다.");
            return Mono.just(false);
        }
        
        String jti = jwt.getClaimAsString("jti"); // JWT ID
        if (jti == null) {
            // JTI가 없으면 sub + iat으로 고유 식별자 생성
            String sub = jwt.getClaimAsString("sub");
            Long iat = jwt.getClaimAsInstant("iat") != null ? jwt.getClaimAsInstant("iat").getEpochSecond() : null;
            if (sub != null && iat != null) {
                jti = sub + ":" + iat;
            } else {
                return Mono.just(false);
            }
        }
        
        String key = "blacklisted_jwt:" + jti;
        
        // 토큰의 만료 시간까지 블랙리스트에 유지
        Long exp = jwt.getExpiresAt() != null ? jwt.getExpiresAt().getEpochSecond() : null;
        if (exp != null) {
            long now = java.time.Instant.now().getEpochSecond();
            long ttl = exp - now;
            if (ttl > 0) {
                return redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofSeconds(ttl));
            }
        }
        
        // 만료 시간이 없으면 24시간 동안 블랙리스트
        return redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofHours(24));
    }

    /**
     * Auth0 로그인 시작 (항상 새 로그인)
     */
    @GetMapping("/login")
    @Operation(summary = "Auth0 로그인 시작", description = "Auth0 OAuth2 로그인 시작")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "OAuth2 로그인 시작으로 리다이렉트")
    })
    public Mono<ResponseEntity<Void>> login(ServerWebExchange exchange) {
        
        // 기존 세션을 무효화하여 새로운 로그인 강제
        return exchange.getSession()
                .flatMap(session -> {
                    session.invalidate();
                    return Mono.just(ResponseEntity.status(302)
                            .location(java.net.URI.create("/oauth2/authorization/auth0?prompt=login"))
                            .build());
                });
    }

//    /**
//     * 강제 새 로그인 (기존 세션 무효화)
//     */
//    @GetMapping("/fresh-login")
//    @Operation(summary = "강제 새 로그인", description = "기존 세션 무효화 후 새 로그인")
//    @ApiResponses({
//        @ApiResponse(responseCode = "302", description = "로그아웃 후 로그인 페이지로 리다이렉트")
//    })
//    public Mono<ResponseEntity<Void>> freshLogin() {
//        String baseIssuer = issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
//        String logoutUrl = String.format(
//                "%s/v2/logout?client_id=%s&returnTo=%s",
//                baseIssuer,
//                urlEncode(clientId),
//                urlEncode("http://localhost:8080/auth/login-page")
//        );
//
//        return Mono.just(ResponseEntity.status(302)
//                .location(java.net.URI.create(logoutUrl))
//                .build());
//    }

//    /**
//     * 로그인 페이지 (로그아웃 후 리다이렉트용)
//     */
//    @GetMapping("/login-page")
//    @Operation(summary = "로그인 페이지", description = "로그아웃 후 자동 로그인 페이지")
//    @ApiResponses({
//        @ApiResponse(responseCode = "302", description = "OAuth2 로그인 시작으로 리다이렉트")
//    })
//    public Mono<ResponseEntity<Void>> loginPage() {
//        return Mono.just(ResponseEntity.status(302)
//                .location(java.net.URI.create("/oauth2/authorization/auth0"))
//                .build());
//    }

    /**
     * 로그아웃 엔드포인트 - Auth0 SSO 세션까지 완전 종료 (POST)
     */
    @PostMapping("/logout")
    @Operation(summary = "Auth0 완전 로그아웃", description = "로컬 세션을 종료하고 Auth0 SSO 로그아웃 URL을 제공합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그아웃 성공, Auth0 SSO 로그아웃 URL 포함"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    public Mono<ResponseEntity<Object>> logout(@AuthenticationPrincipal Jwt jwt, ServerWebExchange exchange) {
        if (jwt == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "로그아웃하려면 먼저 로그인해야 합니다");
            error.put("status", "unauthorized");
            error.put("loginUrl", "/auth/login");
            return Mono.just(ResponseEntity.status(401).body((Object) error));
        }
        return performLogout(exchange);
    }

    /**
     * 로그아웃 엔드포인트 - Auth0 SSO 세션까지 완전 종료 (GET)
     */
    @GetMapping("/logout")
    @Operation(summary = "Auth0 완전 로그아웃 (GET)", description = "GET 방식으로 로컬 세션을 종료하고 Auth0 SSO 로그아웃 URL을 제공합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그아웃 성공, Auth0 SSO 로그아웃 URL 포함"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    public Mono<ResponseEntity<Object>> logoutGet(@AuthenticationPrincipal Jwt jwt, ServerWebExchange exchange) {
        if (jwt == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "로그아웃하려면 먼저 로그인해야 합니다");
            error.put("status", "unauthorized");
            error.put("loginUrl", "/auth/login");
            return Mono.just(ResponseEntity.status(401).body((Object) error));
        }
        return performLogout(exchange);
    }

    /**
     * 실제 로그아웃 수행 로직
     */
    private Mono<ResponseEntity<Object>> performLogout(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
            .cast(org.springframework.security.core.context.SecurityContext.class)
            .flatMap(securityContext -> {
                org.springframework.security.core.Authentication authentication = securityContext.getAuthentication();
                
                // 인증된 사용자인지 확인
                if (authentication == null || !authentication.isAuthenticated() || 
                    !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt)) {
                    
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "인증된 사용자만 로그아웃할 수 있습니다");
                    error.put("status", "unauthorized");
                    error.put("loginUrl", "/auth/login");
                    
                    return Mono.just(ResponseEntity.status(401).body((Object) error));
                }
                
                org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal();
                String userId = jwt.getClaimAsString("sub");
                String userEmail = jwt.getClaimAsString("email");
                
                // 로그아웃 이벤트 로깅
                try {
                    Map<String, Object> logoutEvent = new HashMap<>();
                    logoutEvent.put("eventType", "USER_LOGOUT");
                    logoutEvent.put("userId", userId);
                    logoutEvent.put("userEmail", userEmail);
                    logoutEvent.put("timestamp", java.time.Instant.now().toString());
                    logoutEvent.put("source", "auth0-sso");
                    
                    // 로깅 서비스로 로그아웃 이벤트 전송 (필요시 구현)
                    // gatewayLogService.logEvent(logoutEvent);
                } catch (Exception e) {
                    // 로그 전송 실패 시 무시
                }
                
                // 1단계: JWT 토큰 블랙리스트 추가 (Stateless JWT 무효화)
                return blacklistJwtToken(jwt)
                    .flatMap(blacklistSuccess -> {
                        // 2단계: 로컬 세션 무효화 (있을 경우)
                        return exchange.getSession()
                            .flatMap(session -> {
                                session.invalidate();
                                
                                // 3단계: JSON 응답으로 로그아웃 성공 반환
                                Map<String, Object> response = new HashMap<>();
                                response.put("message", "로그아웃이 성공적으로 완료되었습니다");
                                response.put("status", "logged_out");
                                response.put("userId", userId);
                                response.put("userEmail", userEmail);
                                response.put("timestamp", java.time.Instant.now().toString());
                                response.put("loginUrl", "/auth/login");
                                
                                // JWT 토큰 무효화 상태 포함
                                response.put("tokenInvalidated", blacklistSuccess);
                                response.put("instructions", Map.of(
                                    "frontend", "로컬 저장소에서 JWT 토큰을 제거하세요",
                                    "refreshToken", "Refresh Token도 함께 제거하세요"
                                ));
                                
                                // Auth0 SSO 세션 종료를 위한 URL 제공 (클라이언트가 필요시 사용)
                                String auth0LogoutUrl = buildAuth0LogoutUrl();
                                response.put("auth0LogoutUrl", auth0LogoutUrl);
                                response.put("note", "완전한 SSO 로그아웃을 원하면 auth0LogoutUrl을 방문하세요");
                                
                                return Mono.just(ResponseEntity.ok((Object) response));
                            });
                    });
            })
            .onErrorResume(throwable -> {
                // 인증 정보가 없는 경우 401 반환
                Map<String, Object> error = new HashMap<>();
                error.put("error", "인증 정보를 찾을 수 없습니다");
                error.put("status", "unauthorized");
                error.put("loginUrl", "/auth/login");
                
                return Mono.just(ResponseEntity.status(401).body(error));
            });
    }

    /**
     * Auth0 로그아웃 URL 생성
     * https://domain/v2/logout?client_id=CLIENT_ID&returnTo=RETURN_URL
     */
    private String buildAuth0LogoutUrl() {
        // issuerUri에서 domain 추출 (https://domain.auth0.com/ -> domain.auth0.com)
        String domain = issuerUri.replaceAll("^https?://", "").replaceAll("/$", "");
        
        // Auth0 로그아웃 URL 생성
        return String.format("https://%s/v2/logout?client_id=%s&returnTo=%s",
                domain,
                urlEncode(clientId != null ? clientId : ""),
                urlEncode(logoutRedirectUri));
    }

    /**
     * 로그아웃 성공 후 처리
     */
    @GetMapping("/logout-success")
    @Operation(summary = "로그아웃 성공", description = "Auth0 로그아웃 성공 후 처리")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그아웃 성공 메시지")
    })
    public Mono<ResponseEntity<Map<String, Object>>> logoutSuccess() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "로그아웃이 성공적으로 완료되었습니다");
        response.put("status", "logged_out");
        response.put("loginUrl", "/auth/login");
        response.put("timestamp", java.time.Instant.now().toString());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * OAuth2 로그인 성공 후 사용자 정보 반환
     */
    @GetMapping("/login-success")
    @Operation(summary = "로그인 성공 후 사용자 정보", description = "로그인 성공 후 사용자 정보 반환 (id token, access token 포함)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자 정보 반환 성공")
    })
    public Mono<ResponseEntity<Map<String, Object>>> loginSuccess(
            @Parameter(hidden = true) @AuthenticationPrincipal OidcUser principal,
            ServerWebExchange exchange) {
        if (principal == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "로그인이 필요합니다");
            response.put("loginUrl", "/auth/login");
            response.put("status", "not_authenticated");
            return Mono.just(ResponseEntity.ok(response));
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("message", "Auth0 로그인 성공!");
        userInfo.put("userId", principal.getSubject());
        userInfo.put("email", principal.getEmail());
        userInfo.put("name", principal.getFullName());
        userInfo.put("picture", principal.getPicture());
        userInfo.put("emailVerified", principal.getEmailVerified());
        userInfo.put("authorities", principal.getAuthorities());
        userInfo.put("status", "authenticated");

        // OIDC id_token
        if (principal.getIdToken() != null) {
            userInfo.put("idToken", principal.getIdToken().getTokenValue());
        }

        // 사용자 등록/로그인 이벤트를 카프카로 전송
        try {
            Map<String, Object> authEvent = new HashMap<>();
            authEvent.put("eventType", "USER_LOGIN");
            authEvent.put("userId", principal.getSubject());
            authEvent.put("email", principal.getEmail());
            authEvent.put("name", principal.getFullName());
            authEvent.put("timestamp", java.time.Instant.now().toString());
            authEvent.put("source", "auth0");
            
            // 로깅 서비스로 인증 이벤트 전송 (필요시 구현)
            // gatewayLogService.logEvent(authEvent);
        } catch (Exception e) {
            // 로그 전송 실패 시 무시 (메인 기능에 영향 없도록)
        }

        // access_token 추출 (ReactiveOAuth2AuthorizedClientService 사용)
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                if (ctx.getAuthentication() instanceof OAuth2AuthenticationToken) {
                    OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) ctx.getAuthentication();
                    return authorizedClientService.loadAuthorizedClient(
                            oauthToken.getAuthorizedClientRegistrationId(),
                            oauthToken.getName())
                        .map(client -> {
                            if (client != null && client.getAccessToken() != null) {
                                userInfo.put("accessToken", client.getAccessToken().getTokenValue());
                            } else {
                                userInfo.put("accessToken", null);
                            }
                            return ResponseEntity.ok(userInfo);
                        });
                } else {
                    userInfo.put("accessToken", null);
                    return Mono.just(ResponseEntity.ok(userInfo));
                }
            });
    }

    /**
     * OAuth2 로그인 실패 시 에러 정보 반환
     */
    @GetMapping("/login-error")
    @Operation(summary = "로그인 실패 정보", description = "로그인 실패 시 에러 정보 반환")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "로그인 실패")
    })
    public Mono<ResponseEntity<Map<String, Object>>> loginError() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "로그인 실패");
        error.put("message", "Auth0 로그인 중 오류가 발생했습니다");
        error.put("retryUrl", "/auth/login");

        return Mono.just(ResponseEntity.status(400).body(error));
    }

    /**
     * 현재 인증된 사용자 정보 조회
     */
    @GetMapping("/user-info")
    @Operation(summary = "현재 사용자 정보 조회", description = "현재 로그인된 사용자 정보 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자 정보 반환 성공")
    })
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal OidcUser principal) {
        
        if (principal == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", false);
            response.put("message", "인증되지 않은 사용자");
            response.put("loginUrl", "/auth/login");
            return Mono.just(ResponseEntity.ok(response));
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("authenticated", true);
        userInfo.put("userId", principal.getSubject());
        userInfo.put("email", principal.getEmail());
        userInfo.put("name", principal.getFullName());
        userInfo.put("picture", principal.getPicture());
        userInfo.put("emailVerified", principal.getEmailVerified());
        userInfo.put("authorities", principal.getAuthorities());
        
        return Mono.just(ResponseEntity.ok(userInfo));
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 클라이언트에서 사용할 Auth0 설정 정보 반환
     *
     * @return Auth0 퍼블릭 설정 정보 (domain, clientId, audience)
     */
    @GetMapping("/auth0-config")
    @Operation(summary = "Auth0 설정 정보 조회", description = "클라이언트용 Auth0 퍼블릭 설정 정보 반환")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Auth0 설정 정보를 성공적으로 반환했습니다")
    })
    public ResponseEntity<Map<String, String>> getAuth0Config() {
        // issuerUri에서 domain 추출 (https://domain.auth0.com/ -> domain.auth0.com)
        String domain = issuerUri.replaceAll("^https?://", "").replaceAll("/$", "");

        Map<String, String> config = Map.of(
                "domain", domain,
                "clientId", clientId != null ? clientId : "",
                "audience", audience
        );

        return ResponseEntity.ok(config);
    }

    /**
     * 계정 삭제 엔드포인트
     * User Service로 라우팅하여 실제 삭제 처리
     */
    @DeleteMapping("/account/{userId}")
    @Operation(summary = "계정 삭제", description = "지정된 사용자 계정을 삭제합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "계정이 성공적으로 삭제되었습니다"),
        @ApiResponse(responseCode = "401", description = "인증이 필요합니다"),
        @ApiResponse(responseCode = "403", description = "권한이 부족하거나 최신 JWT 토큰이 필요합니다"),
        @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다")
    })
    public Mono<ResponseEntity<Void>> deleteAccount(
            @PathVariable String userId,
            @Parameter(description = "삭제 사유") @RequestParam(required = false) String reason,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            ServerWebExchange exchange) {
        
        if (jwt == null) {
            // 401 오류는 Spring Security에서 처리하므로 여기서는 단순히 noContent 반환
            return Mono.just(ResponseEntity.status(401).build());
        }
        
        // 실제 삭제 로직은 User Service에서 처리
        // Gateway에서는 요청을 User Service로 라우팅만 함
        return Mono.just(ResponseEntity.noContent().build());
    }

}