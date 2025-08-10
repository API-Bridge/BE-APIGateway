package org.example.APIGatewaySvc.config;

import org.example.APIGatewaySvc.security.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import reactor.core.publisher.Flux;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import java.time.Duration;

/**
 * Spring Cloud Gateway Reactive Security 설정 클래스
 * OAuth2 Resource Server로서 Auth0에서 발급한 JWT 토큰을 검증하여 API Gateway 보안 제공
 * 
 * 주요 기능:
 * - WebFlux 기반 Reactive JWT 토큰 검증
 * - Auth0 JWKS 엔드포인트를 통한 JWT 서명 검증
 * - Audience(오디언스) 검증을 통한 토큰 적합성 확인
 * - 공개 엔드포인트 설정 (/public/**, /actuator/**)
 * - 표준 HTTP 상태 코드 에러 응답
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${auth0.audience}")
    private String audience;

    @Value("${auth0.issuerUri}")
    private String issuer;

    /**
     * Spring Security WebFlux 필터 체인 설정
     * - JWT 기반 인증을 위한 OAuth2 Resource Server 설정
     * - 공개 경로와 보호된 경로 구분
     * - Stateless 인증 (세션 비활성화)
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // CSRF 비활성화 (JWT 토큰 기반 인증 사용)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 폼 로그인 비활성화 (API Gateway는 JWT만 사용)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // HTTP Basic 인증 비활성화
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // 세션 비활성화 (Stateless JWT 인증)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // 경로별 접근 권한 설정
                .authorizeExchange(exchanges -> exchanges
                        // 공개 접근 허용 경로
                        .pathMatchers("/public/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        .pathMatchers("/test/health").permitAll()  // Health check는 인증 불필요
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()  // CORS preflight 요청 허용
                        // 나머지 모든 경로는 인증 필요
                        .anyExchange().authenticated()
                )
                // OAuth2 Resource Server JWT 검증 설정
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                )
                .build();
    }

    /**
     * Reactive JWT Decoder 설정
     * - Auth0 JWKS 엔드포인트에서 공개키를 가져와 JWT 서명 검증
     * - Audience 검증을 통한 토큰 적합성 확인
     * - 네트워크 타임아웃 및 캐시 설정으로 성능 최적화
     * 
     * @return ReactiveJwtDecoder JWT 토큰 디코더
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        // Auth0 JWKS 엔드포인트에서 JWT 디코더 생성
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(issuer + ".well-known/jwks.json")
                // 네트워크 타임아웃 설정 (장애 방어)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // JWT 토큰 검증 설정
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    /**
     * JWT 토큰을 Spring Security Authentication 객체로 변환하는 컨버터
     * - JWT의 permissions 클레임을 Spring Security 권한으로 매핑
     * - Auth0의 권한 체계를 Spring Security와 연동
     * 
     * @return ReactiveJwtAuthenticationConverter JWT 인증 변환기
     */
    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        // JWT 권한 변환기 설정
        JwtGrantedAuthoritiesConverter authoritiesConverter = 
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("permissions");
        authoritiesConverter.setAuthorityPrefix("");

        // Reactive 버전으로 변환
        Converter<Jwt, Flux<GrantedAuthority>> reactiveAuthoritiesConverter = 
                jwt -> Flux.fromIterable(authoritiesConverter.convert(jwt));

        // JWT 인증 변환기 생성
        ReactiveJwtAuthenticationConverter jwtConverter = 
                new ReactiveJwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(reactiveAuthoritiesConverter);

        return jwtConverter;
    }
}