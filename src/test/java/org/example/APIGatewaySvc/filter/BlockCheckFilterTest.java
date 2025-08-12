package org.example.APIGatewaySvc.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockCheckFilterTest {

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
    private HttpHeaders headers;
    
    @Mock
    private SecurityContext securityContext;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private Jwt jwt;
    
    @Mock
    private DataBuffer dataBuffer;

    private BlockCheckFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BlockCheckFilter(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(response.bufferFactory()).thenReturn(mock(org.springframework.core.io.buffer.DataBufferFactory.class));
        when(response.bufferFactory().wrap(any(byte[].class))).thenReturn(dataBuffer);
        when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());
    }

    @Test
    void shouldAllowRequestWhenNotBlocked() {
        // Given
        when(headers.getFirst("X-Forwarded-For")).thenReturn(null);
        when(headers.getFirst("X-Real-IP")).thenReturn(null);
        when(headers.getFirst("X-Api-Key")).thenReturn(null);
        when(redisTemplate.hasKey("blocked:ip:127.0.0.1")).thenReturn(Mono.just(false));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // Mock ReactiveSecurityContextHolder
        when(securityContext.getAuthentication()).thenReturn(null);
        ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain).filter(exchange);
        verify(redisTemplate).hasKey("blocked:ip:127.0.0.1");
    }

    @Test
    void shouldBlockRequestWhenIPIsBlocked() {
        // Given
        when(headers.getFirst("X-Forwarded-For")).thenReturn(null);
        when(headers.getFirst("X-Real-IP")).thenReturn(null);
        when(headers.getFirst("X-Api-Key")).thenReturn(null);
        when(redisTemplate.hasKey("blocked:ip:127.0.0.1")).thenReturn(Mono.just(true));
        when(redisTemplate.getExpire("blocked:ip:127.0.0.1")).thenReturn(Mono.just(Duration.ofSeconds(3600)));
        
        // Mock ReactiveSecurityContextHolder
        when(securityContext.getAuthentication()).thenReturn(null);
        ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldBlockRequestWhenUserIsBlocked() {
        // Given
        String userId = "test-user-123";
        when(headers.getFirst("X-Forwarded-For")).thenReturn(null);
        when(headers.getFirst("X-Real-IP")).thenReturn(null);
        when(headers.getFirst("X-Api-Key")).thenReturn(null);
        when(redisTemplate.hasKey("blocked:ip:127.0.0.1")).thenReturn(Mono.just(false));
        when(redisTemplate.hasKey("blocked:user:" + userId)).thenReturn(Mono.just(true));
        when(redisTemplate.getExpire("blocked:user:" + userId)).thenReturn(Mono.just(Duration.ofSeconds(-1)));
        
        // Mock JWT authentication
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(userId);
        ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldBlockRequestWhenAPIKeyIsBlocked() {
        // Given
        String apiKey = "test-api-key";
        when(headers.getFirst("X-Forwarded-For")).thenReturn(null);
        when(headers.getFirst("X-Real-IP")).thenReturn(null);
        when(headers.getFirst("X-Api-Key")).thenReturn(apiKey);
        when(redisTemplate.hasKey("blocked:ip:127.0.0.1")).thenReturn(Mono.just(false));
        when(redisTemplate.hasKey("blocked:key:" + apiKey)).thenReturn(Mono.just(true));
        when(redisTemplate.getExpire("blocked:key:" + apiKey)).thenReturn(Mono.just(Duration.ofSeconds(1800)));
        
        // Mock ReactiveSecurityContextHolder
        when(securityContext.getAuthentication()).thenReturn(null);
        ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldExtractIPFromXForwardedFor() {
        // Given
        when(headers.getFirst("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
        when(headers.getFirst("X-Real-IP")).thenReturn(null);
        when(headers.getFirst("X-Api-Key")).thenReturn(null);
        when(redisTemplate.hasKey("blocked:ip:192.168.1.100")).thenReturn(Mono.just(false));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        // Mock ReactiveSecurityContextHolder
        when(securityContext.getAuthentication()).thenReturn(null);
        ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext));

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(redisTemplate).hasKey("blocked:ip:192.168.1.100");
        verify(chain).filter(exchange);
    }

    @Test
    void shouldHaveHighestPrecedence() {
        // When & Then
        assert filter.getOrder() == org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
    }
}