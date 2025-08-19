package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * OAuth2 Access Token 및 JWT를 마이크로서비스로 전달하는 커스텀 필터
 * 
 * 주요 기능:
 * - JwtAuthenticationToken과 OAuth2AuthenticationToken 타입 모두 지원
 * - JwtAuthenticationToken인 경우: JWT를 Authorization 헤더에 추가
 * - OAuth2AuthenticationToken인 경우: OAuth2 Access Token을 Authorization 헤더에 추가
 * - 인증되지 않은 요청은 그대로 통과 (공개 API 허용)
 * - Bearer 토큰 형태로 전달하여 마이크로서비스에서 JWT 검증 가능
 */
@Slf4j
// @Component  // OAuth2 Client 제거로 인해 비활성화
public class OAuth2TokenRelayFilter extends AbstractGatewayFilterFactory<OAuth2TokenRelayFilter.Config> {

    @Autowired
    private ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    public OAuth2TokenRelayFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return ReactiveSecurityContextHolder.getContext()
                .cast(org.springframework.security.core.context.SecurityContext.class)
                .map(org.springframework.security.core.context.SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    
                    // 1. JwtAuthenticationToken인 경우: JWT를 추출하여 Authorization 헤더에 추가
                    if (authentication instanceof JwtAuthenticationToken) {
                        JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
                        Jwt jwt = jwtToken.getToken();
                        
                        ServerWebExchange modifiedExchange = exchange.mutate()
                            .request(exchange.getRequest().mutate()
                                .headers(headers -> {
                                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
                                    headers.set("X-User-Subject", jwt.getSubject());
                                    if (jwt.getClaimAsString("email") != null) {
                                        headers.set("X-User-Email", jwt.getClaimAsString("email"));
                                    }
                                    if (jwt.getClaimAsString("name") != null) {
                                        headers.set("X-User-Name", jwt.getClaimAsString("name"));
                                    }
                                })
                                .build())
                            .build();
                        
                        log.debug("JWT 토큰이 마이크로서비스로 전달됨 - Subject: {}", jwt.getSubject());
                        return chain.filter(modifiedExchange);
                    }
                    
                    // 2. OAuth2AuthenticationToken인 경우: OAuth2 Access Token을 추출하여 Authorization 헤더에 추가
                    else if (authentication instanceof OAuth2AuthenticationToken) {
                        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
                        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
                        
                        return authorizedClientRepository
                            .loadAuthorizedClient(registrationId, oauth2Token, exchange)
                            .flatMap(authorizedClient -> {
                                try {
                                    String accessToken = authorizedClient.getAccessToken().getTokenValue();
                                    Object principal = oauth2Token.getPrincipal();
                                    
                                    if (principal instanceof OidcUser) {
                                        OidcUser oidcUser = (OidcUser) principal;
                                        
                                        ServerWebExchange modifiedExchange = exchange.mutate()
                                            .request(exchange.getRequest().mutate()
                                                .headers(headers -> {
                                                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                                                    headers.set("X-User-Subject", oidcUser.getSubject());
                                                    headers.set("X-User-Email", oidcUser.getEmail());
                                                    if (oidcUser.getFullName() != null) {
                                                        headers.set("X-User-Name", oidcUser.getFullName());
                                                    }
                                                })
                                                .build())
                                            .build();
                                        
                                        log.debug("OAuth2 Access Token이 마이크로서비스로 전달됨 - Subject: {}", oidcUser.getSubject());
                                        return chain.filter(modifiedExchange);
                                    }
                                } catch (Exception e) {
                                    log.debug("OAuth2 토큰 처리 중 오류: {} - 토큰 없이 요청 전달", e.getMessage());
                                }
                                return chain.filter(exchange);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.debug("OAuth2AuthorizedClient를 찾을 수 없음 - 토큰 없이 요청 전달");
                                return chain.filter(exchange);
                            }));
                    }
                    
                    // 3. 기타 인증 타입이거나 인증되지 않은 경우: 토큰 없이 요청 전달
                    log.debug("지원되지 않는 인증 타입 또는 인증되지 않은 요청 - 토큰 없이 요청 전달. 인증 타입: {}", 
                        authentication != null ? authentication.getClass().getSimpleName() : "null");
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Security Context가 없는 경우도 그대로 전달
                    log.debug("Security Context가 없는 요청 - 토큰 없이 요청 전달");
                    return chain.filter(exchange);
                }))
                .onErrorResume(throwable -> {
                    // 모든 오류는 catch하고 요청을 계속 진행
                    log.debug("OAuth2TokenRelayFilter에서 오류 발생: {} - 토큰 없이 요청 전달", throwable.getMessage());
                    return chain.filter(exchange);
                });
        };
    }

    public static class Config {
        // 필요시 설정 추가 가능
    }
}