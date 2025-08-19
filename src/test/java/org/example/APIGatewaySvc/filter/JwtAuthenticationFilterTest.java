package org.example.APIGatewaySvc.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * JWT 인증 필터 테스트
 */
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private WebFilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter();
        filterChain = mock(WebFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void testPublicPathAllowed() {
        // 공개 경로는 JWT 토큰 없이도 통과해야 함
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void testOptionsRequestAllowed() {
        // OPTIONS 요청은 JWT 토큰 없이도 통과해야 함 (CORS preflight)
        MockServerHttpRequest request = MockServerHttpRequest.options("/protected").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void testProtectedPathWithoutToken() {
        // 보호된 경로에 토큰 없이 접근 시 401 반환
        MockServerHttpRequest request = MockServerHttpRequest.get("/protected").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(exchange);
    }

    @Test
    void testProtectedPathWithValidToken() {
        // 보호된 경로에 유효한 토큰으로 접근 시 통과
        MockServerHttpRequest request = MockServerHttpRequest.get("/protected")
            .header("Authorization", "Bearer valid-jwt-token")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();

        verify(filterChain).filter(exchange);
    }

    @Test
    void testProtectedPathWithInvalidTokenFormat() {
        // 잘못된 토큰 형식으로 접근 시 401 반환
        MockServerHttpRequest request = MockServerHttpRequest.get("/protected")
            .header("Authorization", "Invalid token format")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(exchange);
    }

    @Test
    void testSwaggerPathAllowed() {
        // Swagger 경로는 JWT 토큰 없이도 통과해야 함
        MockServerHttpRequest request = MockServerHttpRequest.get("/swagger-ui/index.html").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();

        verify(filterChain).filter(exchange);
    }
}