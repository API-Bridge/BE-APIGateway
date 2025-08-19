package org.example.APIGatewaySvc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Auth0 인증을 직접 처리하는 서비스
 * Spring Security OAuth2 대신 수동으로 Auth0 Flow 구현
 */
@Slf4j
@Service
public class Auth0Service {

    @Value("${auth0.issuerUri}")
    private String auth0Domain;

    @Value("${auth0.client-id}")
    private String clientId;

    @Value("${AUTH0_CLIENT_SECRET:cL_HwcnMk583zVfP3gMpttfwsNXZDSr8S6g9Sr7MfePMUdzq8y_RZj8v06xIaF7p}")
    private String clientSecret;

    @Value("${auth0.audience}")
    private String audience;

    private final WebClient webClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public Auth0Service() {
        this.webClient = WebClient.builder().build();
    }

    /**
     * Auth0 로그인 URL 생성
     */
    public String generateLoginUrl(String callbackUrl) {
        String state = generateState();
        
        try {
            String loginUrl = UriComponentsBuilder.fromHttpUrl(auth0Domain + "authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", callbackUrl)
                    .queryParam("scope", "openid profile email")
                    .queryParam("state", state)
                    .queryParam("audience", audience)
                    .toUriString();
            
            log.info("🔗 Generated Auth0 login URL: {}", loginUrl);
            return loginUrl;
            
        } catch (Exception e) {
            log.error("Auth0 로그인 URL 생성 실패", e);
            throw new RuntimeException("Auth0 로그인 URL 생성 실패", e);
        }
    }

    /**
     * Authorization Code를 Access Token으로 교환
     */
    public Mono<Map<String, Object>> exchangeCodeForToken(String code, String callbackUrl) {
        log.info("🔄 Auth0 토큰 교환 시작 - code: {}", code);
        
        String tokenUrl = auth0Domain + "oauth/token";
        
        Map<String, String> tokenRequest = Map.of(
            "grant_type", "authorization_code",
            "client_id", clientId,
            "client_secret", clientSecret,
            "code", code,
            "redirect_uri", callbackUrl
        );

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tokenRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (Map<String, Object>) response)
                .doOnSuccess(response -> {
                    log.info("✅ Auth0 토큰 교환 성공");
                    log.debug("토큰 응답: {}", response);
                })
                .doOnError(error -> {
                    log.error("❌ Auth0 토큰 교환 실패", error);
                });
    }

    /**
     * Access Token으로 사용자 정보 조회
     */
    public Mono<Map<String, Object>> getUserInfo(String accessToken) {
        log.info("👤 Auth0 사용자 정보 조회 시작");
        
        String userInfoUrl = auth0Domain + "userinfo";

        return webClient.get()
                .uri(userInfoUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .map(userInfo -> (Map<String, Object>) userInfo)
                .doOnSuccess(userInfo -> {
                    log.info("✅ Auth0 사용자 정보 조회 성공 - user: {}", userInfo.get("email"));
                })
                .doOnError(error -> {
                    log.error("❌ Auth0 사용자 정보 조회 실패", error);
                });
    }

    /**
     * Auth0 로그아웃 URL 생성
     */
    public String generateLogoutUrl(String returnToUrl) {
        try {
            String encodedReturnTo = URLEncoder.encode(returnToUrl, StandardCharsets.UTF_8);
            
            return String.format(
                "%sv2/logout?client_id=%s&returnTo=%s",
                auth0Domain,
                clientId,
                encodedReturnTo
            );
        } catch (Exception e) {
            log.error("Auth0 로그아웃 URL 생성 실패", e);
            throw new RuntimeException("Auth0 로그아웃 URL 생성 실패", e);
        }
    }

    /**
     * 보안을 위한 State 파라미터 생성
     */
    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * JWT 토큰 유효성 검증 (간단한 구현)
     */
    public Mono<Boolean> validateToken(String token) {
        // 실제 환경에서는 Auth0 JWKS를 사용하여 서명 검증
        // 여기서는 간단히 토큰 형식만 확인
        if (token == null || token.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Mono.just(false);
        }
        
        // 실제로는 Auth0 JWKS 엔드포인트에서 공개키를 가져와서 검증해야 함
        log.debug("JWT 토큰 기본 형식 검증 통과");
        return Mono.just(true);
    }
}