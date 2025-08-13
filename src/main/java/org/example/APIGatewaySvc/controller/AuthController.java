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
import org.example.APIGatewaySvc.service.KafkaProducerService;

/**
 * Auth0 OAuth2 로그인 처리를 위한 컨트롤러
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Auth0 OAuth2 인증 관련 API")
public class AuthController {

    @Value("${auth0.issuerUri}")
    private String issuerUri;

    @Value("${auth0.client-id:}")
    private String clientId;

    @Value("${auth0.audience}")
    private String audience;

    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    private final KafkaProducerService kafkaProducerService;

    public AuthController(ReactiveOAuth2AuthorizedClientService authorizedClientService,
                         KafkaProducerService kafkaProducerService) {
        this.authorizedClientService = authorizedClientService;
        this.kafkaProducerService = kafkaProducerService;
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
     * 로그아웃 엔드포인트
     */
    @PostMapping("/logout")
    @Operation(summary = "Auth0 로그아웃", description = "Auth0를 통한 로그아웃 실행")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Auth0 로그아웃으로 리다이렉트")
    })
    public Mono<ResponseEntity<Object>> logout(ServerWebExchange exchange) {
        // 로그아웃 이벤트를 카프카로 전송
        return ReactiveSecurityContextHolder.getContext()
            .cast(org.springframework.security.core.context.SecurityContext.class)
            .flatMap(securityContext -> {
                try {
                    org.springframework.security.core.Authentication authentication = securityContext.getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt) {
                        org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) authentication.getPrincipal();
                        
                        Map<String, Object> logoutEvent = new HashMap<>();
                        logoutEvent.put("eventType", "USER_LOGOUT");
                        logoutEvent.put("userId", jwt.getClaimAsString("sub"));
                        logoutEvent.put("timestamp", java.time.Instant.now().toString());
                        logoutEvent.put("source", "auth0");
                        
                        kafkaProducerService.sendAuthEvent(logoutEvent);
                    }
                } catch (Exception e) {
                    // 로그 전송 실패 시 무시
                }
                
                // Spring Security의 logout 메커니즘을 통해 처리됨
                return exchange.getSession()
                        .flatMap(session -> {
                            session.invalidate();
                            return Mono.just(ResponseEntity.status(302)
                                    .location(java.net.URI.create("/logout"))
                                    .build());
                        });
            })
            .onErrorResume(throwable -> {
                // 인증 정보가 없는 경우에도 로그아웃 진행
                return exchange.getSession()
                        .flatMap(session -> {
                            session.invalidate();
                            return Mono.just(ResponseEntity.status(302)
                                    .location(java.net.URI.create("/logout"))
                                    .build());
                        });
            });
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
            
            kafkaProducerService.sendAuthEvent(authEvent);
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

}