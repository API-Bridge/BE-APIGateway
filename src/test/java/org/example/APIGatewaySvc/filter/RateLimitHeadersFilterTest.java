package org.example.APIGatewaySvc.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitHeadersFilterTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private HttpHeaders responseHeaders;

    @Mock
    private HttpHeaders requestHeaders;

    private RateLimitHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitHeadersFilter(redisTemplate);

        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(request.getHeaders()).thenReturn(requestHeaders);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldAddRateLimitHeaders() {
        // Given
        setupBasicRequest();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        // Redis에서 토큰 정보 반환
        when(valueOperations.get(anyString())).thenReturn(
            Mono.just("15"), // 남은 토큰
            Mono.just(String.valueOf(System.currentTimeMillis() / 1000)) // 타임스탬프
        );

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        verify(responseHeaders).add("X-RateLimit-Limit", "20");
        verify(responseHeaders).add("X-RateLimit-Remaining", "15");
        verify(responseHeaders).add(eq("X-RateLimit-Reset"), anyString());
    }

    @Test
    void shouldHandleRedisError() {
        // Given
        setupBasicRequest();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        // Redis 에러 시뮬레이션
        when(valueOperations.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis error")));

        // When & Then - 에러가 발생해도 필터는 정상적으로 완료되어야 함
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // 에러 상황에서는 헤더가 추가되지 않음
        verify(responseHeaders, never()).add(eq("X-RateLimit-Limit"), anyString());
    }

    @Test
    void shouldHandleNoRateLimitKey() {
        // Given
        when(request.getRemoteAddress()).thenReturn(null);
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn(null);
        when(requestHeaders.getFirst("X-Real-IP")).thenReturn(null);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then - Rate Limit 키가 없으면 헤더 추가되지 않음
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldExtractClientIpFromXForwardedFor() {
        // Given
        setupBasicRequest();
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        when(valueOperations.get(anyString())).thenReturn(
            Mono.just("10"),
            Mono.just("1640000000")
        );

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        verify(responseHeaders).add("X-RateLimit-Remaining", "10");
    }

    @Test
    void shouldExtractClientIpFromXRealIp() {
        // Given
        setupBasicRequest();
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn(null);
        when(requestHeaders.getFirst("X-Real-IP")).thenReturn("203.0.113.1");
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        when(valueOperations.get(anyString())).thenReturn(
            Mono.just("5"),
            Mono.just("1640000000")
        );

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        verify(responseHeaders).add("X-RateLimit-Remaining", "5");
    }

    @Test
    void shouldHandleZeroRemainingTokens() {
        // Given
        setupBasicRequest();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        // Redis에서 0개 토큰 반환
        when(valueOperations.get(anyString())).thenReturn(
            Mono.just("0"),
            Mono.just(String.valueOf(System.currentTimeMillis() / 1000))
        );

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        verify(responseHeaders).add("X-RateLimit-Remaining", "0");
    }

    @Test
    void shouldHaveCorrectOrder() {
        // When & Then
        assertTrue(filter.getOrder() > 0);
        assertEquals(Integer.MAX_VALUE - 10, filter.getOrder());
    }

    @Test
    void shouldHandleEmptyRedisResponse() {
        // Given
        setupBasicRequest();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        // Redis에서 빈 응답
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then - 기본값으로 헤더 추가
        verify(responseHeaders).add("X-RateLimit-Limit", "20");
        verify(responseHeaders).add("X-RateLimit-Remaining", "0");
    }

    private void setupBasicRequest() {
        when(request.getURI()).thenReturn(URI.create("/test/path"));
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn(null);
        when(requestHeaders.getFirst("X-Real-IP")).thenReturn(null);
        
        // 기본 Redis 응답 설정
        when(valueOperations.get(anyString())).thenReturn(
            Mono.just("15"),
            Mono.just(String.valueOf(System.currentTimeMillis() / 1000))
        );
    }
}