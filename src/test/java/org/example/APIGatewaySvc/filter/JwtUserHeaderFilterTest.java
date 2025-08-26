package org.example.APIGatewaySvc.filter;

import org.example.APIGatewaySvc.util.JwtRoleUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtUserHeaderFilterTest {

    @Mock
    private JwtRoleUtils jwtRoleUtils;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpRequest.Builder requestBuilder;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private Authentication authentication;

    @Mock
    private Jwt jwt;

    private JwtUserHeaderFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new JwtUserHeaderFilter(jwtRoleUtils);
    }

    @Test
    @DisplayName("JWT 토큰에서 사용자 정보를 추출하여 헤더에 추가")
    void shouldExtractUserInfoFromJwtAndAddHeaders() {
        // Given
        String userId = "auth0|12345";
        String userEmail = "user@example.com";
        List<String> roles = List.of("user", "admin");
        List<String> permissions = List.of("read:users", "write:users");

        when(exchange.<Authentication>getPrincipal()).thenReturn(Mono.just(authentication));
        when(jwtRoleUtils.extractJwtFromAuthentication(authentication)).thenReturn(jwt);
        when(jwtRoleUtils.extractAuth0Id(jwt)).thenReturn(userId);
        when(jwtRoleUtils.extractEmail(jwt)).thenReturn(userEmail);
        // when(jwtRoleUtils.extractRoles(jwt)).thenReturn(roles); // 메서드 삭제됨
        when(jwtRoleUtils.extractPermissions(jwt)).thenReturn(permissions);

        when(exchange.getRequest()).thenReturn(request);
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(request);
        
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(exchange);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        GatewayFilter gatewayFilter = filter.apply(new JwtUserHeaderFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(requestBuilder, times(1)).header("X-User-Id", userId);
        verify(requestBuilder, times(1)).header("X-User-Email", userEmail);
        verify(requestBuilder, times(1)).header("X-User-Role", "user");
        verify(requestBuilder, times(1)).header("X-User-Roles", "user,admin");
        verify(requestBuilder, times(1)).header("X-User-Permissions", "read:users,write:users");
        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("JWT 토큰이 없을 때 헤더 추가 건너뜀")
    void shouldSkipHeaderAdditionWhenJwtIsNull() {
        // Given
        when(exchange.<Authentication>getPrincipal()).thenReturn(Mono.just(authentication));
        when(jwtRoleUtils.extractJwtFromAuthentication(authentication)).thenReturn(null);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        GatewayFilter gatewayFilter = filter.apply(new JwtUserHeaderFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        verify(exchange, never()).mutate();
    }

    @Test
    @DisplayName("Authentication이 없을 때 헤더 추가 건너뜀")
    void shouldSkipHeaderAdditionWhenAuthenticationIsEmpty() {
        // Given
        when(exchange.<Authentication>getPrincipal()).thenReturn(Mono.empty());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        GatewayFilter gatewayFilter = filter.apply(new JwtUserHeaderFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(jwtRoleUtils);
    }

    @Test
    @DisplayName("사용자 정보가 부분적으로만 있을 때 처리")
    void shouldHandlePartialUserInfo() {
        // Given
        String userId = "auth0|12345";
        // 이메일과 역할은 없음
        List<String> roles = List.of();
        List<String> permissions = List.of();

        when(exchange.<Authentication>getPrincipal()).thenReturn(Mono.just(authentication));
        when(jwtRoleUtils.extractJwtFromAuthentication(authentication)).thenReturn(jwt);
        when(jwtRoleUtils.extractAuth0Id(jwt)).thenReturn(userId);
        when(jwtRoleUtils.extractEmail(jwt)).thenReturn(null);
        // when(jwtRoleUtils.extractRoles(jwt)).thenReturn(roles); // 메서드 삭제됨
        when(jwtRoleUtils.extractPermissions(jwt)).thenReturn(permissions);

        when(exchange.getRequest()).thenReturn(request);
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(request);
        
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(exchange);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        GatewayFilter gatewayFilter = filter.apply(new JwtUserHeaderFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(requestBuilder, times(1)).header("X-User-Id", userId);
        verify(requestBuilder, never()).header(eq("X-User-Email"), any());
        verify(requestBuilder, never()).header(eq("X-User-Role"), any());
        verify(requestBuilder, never()).header(eq("X-User-Roles"), any());
        verify(requestBuilder, never()).header(eq("X-User-Permissions"), any());
        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("오류 발생 시 요청 계속 진행")
    void shouldContinueOnError() {
        // Given
        when(exchange.<Authentication>getPrincipal()).thenReturn(Mono.just(authentication));
        when(jwtRoleUtils.extractJwtFromAuthentication(authentication))
                .thenThrow(new RuntimeException("JWT 처리 오류"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // When
        GatewayFilter gatewayFilter = filter.apply(new JwtUserHeaderFilter.Config());
        Mono<Void> result = gatewayFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }
}