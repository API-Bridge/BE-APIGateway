package org.example.APIGatewaySvc.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * BlockCheckFilter 단위 테스트
 * 간소화된 Mock 기반 테스트
 * TODO: BlockCheckFilter 구현 후 활성화
 */
// @ExtendWith(MockitoExtension.class)
class BlockCheckFilterTestDisabled {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GatewayFilterChain filterChain;

    private BlockCheckFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        filter = new BlockCheckFilter(redisTemplate);
    }

    // @Test
    void shouldAllowRequestWhenNotBlocked() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build()
        );
        
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
    }

    // @Test
    void shouldBlockRequestWhenIPIsBlocked() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build()
        );
        
        when(valueOperations.get("blocked:ip:192.168.1.100"))
            .thenReturn(Mono.just("blocked"));

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        // 차단된 경우 filterChain이 호출되지 않아야 함
        verify(filterChain, never()).filter(exchange);
        
        // 응답 상태가 403이어야 함
        ServerHttpResponse response = exchange.getResponse();
        assert response.getStatusCode() == HttpStatus.FORBIDDEN;
    }

    // @Test
    void shouldExtractIPFromXForwardedForHeader() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build()
        );
        
        when(valueOperations.get("blocked:ip:203.0.113.195"))
            .thenReturn(Mono.just("blocked"));

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain, never()).filter(exchange);
        
        // X-Forwarded-For 헤더의 첫 번째 IP로 차단 확인했는지 검증
        verify(valueOperations).get("blocked:ip:203.0.113.195");
    }

    // @Test
    void shouldCheckUserIdBlockingWhenAuthenticated() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .header("Authorization", "Bearer mock-jwt-token")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build()
        );
        
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
    }

    // @Test
    void shouldHandleRedisConnectionError() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build()
        );
        
        when(valueOperations.get(anyString()))
            .thenReturn(Mono.error(new RuntimeException("Redis connection error")));
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        // Redis 오류 시에도 요청은 통과시켜야 함
        verify(filterChain).filter(exchange);
    }
}