package org.example.APIGatewaySvc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KeyResolverConfig 단위 테스트
 * Rate Limiting을 위한 키 생성 전략 검증
 */
class KeyResolverConfigTest {

    private KeyResolverConfig keyResolverConfig;
    private KeyResolver userKeyResolver;
    private KeyResolver ipKeyResolver;

    @BeforeEach
    void setUp() {
        keyResolverConfig = new KeyResolverConfig();
        userKeyResolver = keyResolverConfig.userKeyResolver();
        ipKeyResolver = keyResolverConfig.ipKeyResolver();
    }

    @Test
    @DisplayName("인증된 사용자의 경우 사용자 ID 기반 키를 반환해야 함")
    void shouldReturnUserIdBasedKeyForAuthenticatedUser() {
        // Given
        String userId = "auth0|test-user-123";
        ServerWebExchange exchange = createExchangeWithJWT(userId);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("user:" + userId)
                .verifyComplete();
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 경우 IP 기반 키를 반환해야 함")
    void shouldReturnIpBasedKeyForUnauthenticatedUser() {
        // Given
        String clientIP = "192.168.1.100";
        ServerWebExchange exchange = createExchangeWithoutJWT(clientIP);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + clientIP)
                .verifyComplete();
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더에서 클라이언트 IP를 정확히 추출해야 함")
    void shouldExtractClientIpFromXForwardedForHeader() {
        // Given
        String realClientIP = "203.0.113.45";
        String proxyIP = "10.0.0.1";
        String xForwardedFor = realClientIP + ", " + proxyIP;
        
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-Forwarded-For", xForwardedFor)
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + realClientIP)
                .verifyComplete();
    }

    @Test
    @DisplayName("X-Real-IP 헤더에서 클라이언트 IP를 추출해야 함")
    void shouldExtractClientIpFromXRealIpHeader() {
        // Given
        String realIP = "198.51.100.42";
        
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-Real-IP", realIP)
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + realIP)
                .verifyComplete();
    }

    @Test
    @DisplayName("CF-Connecting-IP 헤더에서 클라이언트 IP를 추출해야 함")
    void shouldExtractClientIpFromCloudflareHeader() {
        // Given
        String cloudflareIP = "203.0.113.100"; // 공개 IP 사용
        
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("CF-Connecting-IP", cloudflareIP)
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + cloudflareIP)
                .verifyComplete();
    }

    @Test
    @DisplayName("사설망 IP는 필터링되고 RemoteAddress를 사용해야 함")
    void shouldFilterPrivateIpAndUseRemoteAddress() {
        // Given
        String privateIP = "192.168.1.1";  // 사설망 IP
        String publicIP = "203.0.113.100"; // RemoteAddress에서 가져올 공개 IP
        
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-Forwarded-For", privateIP)
                .remoteAddress(new InetSocketAddress(publicIP, 8080))
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + publicIP)
                .verifyComplete();
    }

    @Test
    @DisplayName("IP Key Resolver는 항상 IP 기반 키를 반환해야 함")
    void ipKeyResolverShouldAlwaysReturnIpBasedKey() {
        // Given
        String clientIP = "198.51.100.123";
        ServerWebExchange exchange = createExchangeWithJWT("user123"); // JWT가 있어도 IP 키 반환
        exchange = addClientIPToExchange(exchange, clientIP);

        // When
        Mono<String> keyMono = ipKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + clientIP)
                .verifyComplete();
    }

    @Test
    @DisplayName("잘못된 형식의 IP 주소는 unknown으로 처리되어야 함")
    void shouldHandleInvalidIpAddress() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-Forwarded-For", "invalid-ip-address")
                .build(); // RemoteAddress도 null
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:unknown")
                .verifyComplete();
    }

    @Test
    @DisplayName("빈 JWT subject는 IP 기반 키로 폴백되어야 함")
    void shouldFallbackToIpWhenJwtSubjectIsEmpty() {
        // Given
        String clientIP = "203.0.113.50";
        ServerWebExchange exchange = createExchangeWithEmptyJWT(clientIP);

        // When
        Mono<String> keyMono = userKeyResolver.resolve(exchange);

        // Then
        StepVerifier.create(keyMono)
                .expectNext("ip:" + clientIP)
                .verifyComplete();
    }

    /**
     * JWT 토큰이 있는 ServerWebExchange 생성 (테스트용)
     */
    private ServerWebExchange createExchangeWithJWT(String userId) {
        // Mock JWT 생성
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId);

        // Mock Authentication 생성
        JwtAuthenticationToken authToken = mock(JwtAuthenticationToken.class);
        when(authToken.getToken()).thenReturn(jwt);

        // Mock ServerWebExchange 생성
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Principal 설정 (Reactive Context에 Authentication 추가)
        return exchange.mutate()
                .principal(Mono.just(authToken))
                .build();
    }

    /**
     * JWT 토큰이 없는 ServerWebExchange 생성 (테스트용)
     */
    private ServerWebExchange createExchangeWithoutJWT(String clientIP) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress(clientIP, 8080))
                .build();
        
        return MockServerWebExchange.from(request);
    }

    /**
     * 빈 JWT subject를 가진 ServerWebExchange 생성 (테스트용)
     */
    private ServerWebExchange createExchangeWithEmptyJWT(String clientIP) {
        // Mock JWT with empty subject
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("");

        JwtAuthenticationToken authToken = mock(JwtAuthenticationToken.class);
        when(authToken.getToken()).thenReturn(jwt);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress(clientIP, 8080))
                .build();
        
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        return exchange.mutate()
                .principal(Mono.just(authToken))
                .build();
    }

    /**
     * ServerWebExchange에 클라이언트 IP 정보 추가 (테스트용)
     */
    private ServerWebExchange addClientIPToExchange(ServerWebExchange exchange, String clientIP) {
        MockServerHttpRequest newRequest = MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress(clientIP, 8080))
                .build();
        
        return MockServerWebExchange.from(newRequest);
    }
}