package org.example.APIGatewaySvc.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Auth0 인증/인가 관련 엔드포인트를 제공하는 컨트롤러
 * 
 * 주요 기능:
 * 1. 로그인 성공/실패 처리
 * 2. Auth0 로그아웃 URL 생성 및 리다이렉트
 * 3. 사용자 인증 정보 조회
 * 4. JWT 토큰 검증 상태 확인
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    // Auth0Service 제거됨 - Spring Security OAuth2 사용

    @Value("${auth0.issuerUri}")
    private String auth0Domain;

    @Value("${auth0.client-id}")
    private String clientId;

    @Value("${auth0.logout-redirect-uri}")
    private String logoutRedirectUri;

    /**
     * 로그인 성공 후 처리
     * OAuth2 Login 성공 시 호출되는 엔드포인트
     * Auth0에서 받은 사용자 정보 전체를 JSON으로 반환
     * 
     * @param exchange ServerWebExchange
     * @return Mono<ResponseEntity> 로그인 성공 응답 (사용자 정보 전체 포함)
     */
    /**
     * 직접 Auth0 로그인 성공 후 사용자 정보 제공
     * Access Token을 받아서 사용자 정보 조회
     */
    @GetMapping("/login-success")
    public Mono<ResponseEntity<Map<String, Object>>> loginSuccess(
            @RequestParam(required = false) String access_token,
            ServerWebExchange exchange) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        
        if (access_token == null || access_token.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Access token이 없습니다. 다시 로그인해주세요.");
            response.put("authenticated", false);
            response.put("login_url", "/auth/login");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
        }

        log.info("🎉 Auth0 로그인 성공 - 사용자 정보 조회 시작");
        
        return auth0Service.getUserInfo(access_token)
                .map(userInfo -> {
                    response.put("status", "success");
                    response.put("message", "🎉 Auth0 직접 로그인 성공!");
                    response.put("authenticated", true);
                    response.put("auth_provider", "Auth0_Direct");
                    
                    // 🔑 ACCESS TOKEN (가장 중요!)
                    response.put("access_token", access_token);
                    response.put("access_token_type", "Bearer");
                    
                    // 👤 사용자 정보
                    response.put("user", userInfo);
                    
                    // 🚀 다음 단계 안내
                    response.put("next_steps", Map.of(
                        "message", "이제 access_token을 사용하여 다른 마이크로서비스에 API 요청을 할 수 있습니다.",
                        "api_example", "Authorization: Bearer " + access_token,
                        "user_service_example", "GET /gateway/user/api/users/me with Authorization header",
                        "token_usage", "이 토큰으로 User Service에서 사용자 생성/조회 가능"
                    ));
                    
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("❌ 사용자 정보 조회 실패", error);
                    response.put("status", "error");
                    response.put("message", "사용자 정보 조회 실패: " + error.getMessage());
                    response.put("access_token", access_token);
                    response.put("error_details", error.getClass().getSimpleName());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
                });
    }
    

    /**
     * 로그인 실패 처리
     * OAuth2 Login 실패 시 호출되는 엔드포인트
     * 
     * @param error 에러 파라미터
     * @param errorDescription 에러 설명
     * @return ResponseEntity 로그인 실패 응답
     */
    @GetMapping("/login-error")
    public ResponseEntity<Map<String, Object>> loginError(
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            ServerWebExchange exchange) {
        
        // 로그로 상세 정보 출력
        log.error("🚨 Auth0 로그인 실패 - error: {}, description: {}, state: {}, code: {}", 
                 error, errorDescription, state, code);
        log.error("🔍 Request URI: {}", exchange.getRequest().getURI());
        log.error("🔍 All query params: {}", exchange.getRequest().getQueryParams());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "🚨 Auth0 로그인 실패");
        response.put("error", error != null ? error : "unknown_error");
        response.put("error_description", errorDescription != null ? errorDescription : "Login process failed");
        response.put("state", state);
        response.put("code", code);
        response.put("timestamp", System.currentTimeMillis());
        
        // 디버깅을 위한 추가 정보
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("auth0_domain", auth0Domain);
        debugInfo.put("auth0_client_id", clientId);
        debugInfo.put("callback_url_expected", "http://localhost:8080/login/oauth2/code/auth0");
        debugInfo.put("logout_redirect_uri", logoutRedirectUri);
        
        // 모든 쿼리 파라미터 수집
        Map<String, String> allParams = new HashMap<>();
        exchange.getRequest().getQueryParams().forEach((key, values) -> {
            allParams.put(key, values.isEmpty() ? null : values.get(0));
        });
        debugInfo.put("all_query_params", allParams);
        
        response.put("debug_info", debugInfo);
        response.put("troubleshooting", Map.of(
            "check_1", "Auth0 대시보드에서 Allowed Callback URLs에 'http://localhost:8080/login/oauth2/code/auth0'가 설정되어 있는지 확인",
            "check_2", "Auth0 대시보드에서 Application Type이 'Regular Web Application'인지 확인",
            "check_3", "Auth0 대시보드에서 Client ID와 Client Secret이 정확한지 확인",
            "check_4", "Auth0 대시보드에서 Domain이 'api-bridge.us.auth0.com'인지 확인"
        ));
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * OAuth2 로그인 디버깅용 엔드포인트
     */
    @GetMapping("/oauth2-debug")
    public ResponseEntity<Map<String, Object>> oauth2Debug() {
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("auth0_domain", auth0Domain);
        debugInfo.put("auth0_client_id", clientId);
        debugInfo.put("logout_redirect_uri", logoutRedirectUri);
        debugInfo.put("expected_callback_url", "http://localhost:8080/login/oauth2/code/auth0");
        debugInfo.put("auth0_authorization_url", auth0Domain + "authorize");
        debugInfo.put("auth0_token_url", auth0Domain + "oauth/token");
        debugInfo.put("auth0_userinfo_url", auth0Domain + "userinfo");
        debugInfo.put("auth0_jwks_url", auth0Domain + ".well-known/jwks.json");
        
        // 직접 Auth0 URL 생성해서 테스트 가능하도록
        String directAuth0Url = String.format(
            "%sauthorize?response_type=code&client_id=%s&redirect_uri=%s&scope=openid profile email&audience=%s",
            auth0Domain,
            clientId, 
            "http://localhost:8080/login/oauth2/code/auth0",
            "http://localhost:8080"
        );
        debugInfo.put("direct_auth0_url", directAuth0Url);
        
        debugInfo.put("troubleshooting_steps", Map.of(
            "step_1", "Auth0 대시보드에서 Application Type이 'Regular Web Application'인지 확인",
            "step_2", "Allowed Callback URLs에 'http://localhost:8080/login/oauth2/code/auth0' 추가",
            "step_3", "Allowed Web Origins에 'http://localhost:8080' 추가",
            "step_4", "APIs에서 'http://localhost:8080' identifier로 API 생성",
            "step_5", "Client ID와 Secret이 정확한지 확인"
        ));
        
        debugInfo.put("manual_test", Map.of(
            "instruction", "위의 direct_auth0_url을 브라우저에 직접 입력하여 Auth0 로그인 페이지가 나오는지 확인",
            "expected_result", "Auth0 로그인 페이지 표시",
            "if_error", "Auth0 Application 설정 문제"
        ));
        
        return ResponseEntity.ok(debugInfo);
    }

    /**
     * Auth0 설정 디버깅용 엔드포인트
     */
    @GetMapping("/debug-config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("auth0Domain", auth0Domain);
        config.put("clientId", clientId);
        config.put("logoutRedirectUri", logoutRedirectUri);
        config.put("timestamp", System.currentTimeMillis());
        
        log.info("🔍 Auth0 설정 디버깅: domain={}, clientId={}, logoutUri={}", 
                auth0Domain, clientId, logoutRedirectUri);
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * 간단한 Auth0 테스트용 엔드포인트
     */
    @GetMapping("/test-auth0-direct")
    public ResponseEntity<Void> testAuth0Direct() {
        String directAuth0Url = String.format(
            "%sauthorize?response_type=code&client_id=%s&redirect_uri=%s&scope=openid profile email&audience=%s",
            auth0Domain,
            clientId, 
            "http://localhost:8080/auth/callback",
            "http://localhost:8080"
        );
        
        log.info("🔗 Direct Auth0 URL: {}", directAuth0Url);
        
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(directAuth0Url))
                .build();
    }

    /**
     * Auth0 로그아웃 URL 조회 (API 호출용)
     * 프론트엔드에서 로그아웃 URL을 받아서 리다이렉트할 때 사용
     * 
     * @return ResponseEntity 로그아웃 URL 응답
     */
    @GetMapping("/logout-url")
    public ResponseEntity<Map<String, Object>> getLogoutUrl() {
        // Auth0 로그아웃 URL 생성
        String logoutUrl = String.format(
            "%sv2/logout?client_id=%s&returnTo=%s",
            auth0Domain.endsWith("/") ? auth0Domain : auth0Domain + "/",
            clientId,
            logoutRedirectUri
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("logoutUrl", logoutUrl);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Auth0 로그아웃 리다이렉트 (브라우저용)
     * Auth0 표준 방식에 따른 GET 리다이렉트 처리
     * 
     * @return ResponseEntity 로그아웃 리다이렉트 응답
     */
    @GetMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Auth0 로그아웃 URL 생성
        String logoutUrl = String.format(
            "%sv2/logout?client_id=%s&returnTo=%s",
            auth0Domain.endsWith("/") ? auth0Domain : auth0Domain + "/",
            clientId,
            logoutRedirectUri
        );
        
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(logoutUrl))
                .build();
    }

    /**
     * 로그아웃 성공 처리
     * Auth0에서 리다이렉트된 후 호출되는 엔드포인트
     * 
     * @return ResponseEntity 로그아웃 완료 응답
     */
    @GetMapping("/logout-success")
    public ResponseEntity<Map<String, Object>> logoutSuccess() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Logout completed successfully");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 정보 조회 (직접 Auth0 구현)
     * Access Token을 사용하여 사용자 정보 조회
     */
    @GetMapping("/user-info")
    public Mono<ResponseEntity<Map<String, Object>>> getUserInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("status", "error");
            response.put("message", "Authorization 헤더가 필요합니다");
            response.put("authenticated", false);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
        }
        
        String accessToken = authHeader.substring(7);
        
        return auth0Service.getUserInfo(accessToken)
                .map(userInfo -> {
                    response.put("status", "success");
                    response.put("authenticated", true);
                    response.put("auth_provider", "Auth0_Direct");
                    response.put("user", userInfo);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    response.put("status", "error");
                    response.put("message", "사용자 정보 조회 실패: " + error.getMessage());
                    response.put("authenticated", false);
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
                });
    }

    /**
     * JWT 토큰 검증 상태 확인 (직접 Auth0 구현)
     */
    @GetMapping("/validate-token")
    public Mono<ResponseEntity<Map<String, Object>>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("valid", false);
            response.put("authenticated", false);
            response.put("message", "Authorization 헤더가 필요합니다");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
        }
        
        String token = authHeader.substring(7);
        
        return auth0Service.validateToken(token)
                .map(isValid -> {
                    response.put("valid", isValid);
                    response.put("authenticated", isValid);
                    if (isValid) {
                        response.put("message", "JWT 토큰이 유효합니다");
                        return ResponseEntity.ok(response);
                    } else {
                        response.put("message", "JWT 토큰이 유효하지 않습니다");
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                    }
                });
    }

    /**
     * Auth0 로그인 시작 엔드포인트
     * 직접 Auth0 로그인 페이지로 리다이렉트
     * 
     * @return ResponseEntity 302 리다이렉트 응답
     */
    @GetMapping("/login")
    public ResponseEntity<Void> startLogin() {
        String callbackUrl = "http://localhost:8080/auth/callback";
        String loginUrl = auth0Service.generateLoginUrl(callbackUrl);
        
        log.info("🚀 Auth0 직접 로그인 시작 - URL: {}", loginUrl);
        
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(loginUrl))
                .build();
    }

    /**
     * Auth0 콜백 처리 엔드포인트
     * Auth0에서 authorization code를 받아서 토큰으로 교환
     */
    @GetMapping("/callback")
    public Mono<ResponseEntity<Void>> handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            @RequestParam(required = false) String state) {
        
        log.info("📞 Auth0 콜백 수신 - code: {}, error: {}", code != null ? "있음" : "없음", error);
        
        if (error != null) {
            log.error("❌ Auth0 콜백 에러 - error: {}, description: {}", error, error_description);
            return Mono.just(ResponseEntity.<Void>status(HttpStatus.FOUND)
                    .location(URI.create("/auth/login-error?error=" + error + "&error_description=" + error_description))
                    .build());
        }
        
        if (code == null) {
            log.error("❌ Auth0 콜백에 code 없음");
            return Mono.just(ResponseEntity.<Void>status(HttpStatus.FOUND)
                    .location(URI.create("/auth/login-error?error=no_code"))
                    .build());
        }
        
        String callbackUrl = "http://localhost:8080/auth/callback";
        
        Mono<ResponseEntity<Void>> result = auth0Service.exchangeCodeForToken(code, callbackUrl)
                .map(tokenResponse -> {
                    String accessToken = (String) tokenResponse.get("access_token");
                    
                    if (accessToken == null) {
                        log.error("❌ 토큰 교환 실패 - access_token 없음");
                        return ResponseEntity.<Void>status(HttpStatus.FOUND)
                                .location(URI.create("/auth/login-error?error=no_access_token"))
                                .build();
                    }
                    
                    // 토큰을 쿼리 파라미터로 전달하여 login-success로 리다이렉트
                    String successUrl = String.format("/auth/login-success?access_token=%s", accessToken);
                    log.info("✅ Auth0 인증 성공 - 토큰으로 리다이렉트");
                    
                    return ResponseEntity.<Void>status(HttpStatus.FOUND)
                            .location(URI.create(successUrl))
                            .build();
                })
                .doOnError(throwable -> log.error("❌ 토큰 교환 중 오류", throwable))
                .cast(ResponseEntity.class)
                .map(entity -> (ResponseEntity<Void>) entity)
                .onErrorReturn(ResponseEntity.<Void>status(HttpStatus.FOUND)
                        .location(URI.create("/auth/login-error?error=token_exchange_failed"))
                        .build());
        
        return result;
    }




}