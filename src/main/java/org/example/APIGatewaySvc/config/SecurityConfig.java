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
import reactor.core.publisher.Mono;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;

import java.time.Duration;

/**
 * Spring Cloud Gateway Reactive Security 설정 클래스
 * OAuth2 Resource Server로서 Auth0에서 발급한 JWT 토큰을 검증하여 API Gateway 보안 제공
 *
 *
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

    @Value("${auth0.logout-redirect-uri}")
    private String logoutRedirectUri;

    /**
     * Spring Security WebFlux 필터 체인 설정
     * - JWT 기반 인증을 위한 OAuth2 Resource Server 설정
     * - 공개 경로와 보호된 경로 구분
     * - Stateless 인증 (세션 비활성화)
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveClientRegistrationRepository clientRegistrationRepository) {
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
                        // Auth0 로그인/로그아웃 관련 경로 허용 (OAuth2 로그인 완료 후 접근 가능)
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/oauth2/**").permitAll()
                        .pathMatchers("/login/**").permitAll()
                        .pathMatchers("/public/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        // 운영에서 actuator 쓰기(POST/DELETE) 비활성화를 위해 GET만 허용
                        .pathMatchers(HttpMethod.GET, "/actuator/**").hasRole("ADMIN")
                        .pathMatchers("/actuator/**").denyAll()
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        // 각 서비스의 OpenAPI 문서 및 swagger-config를 게이트웨이 경유로 허용
                        .pathMatchers(HttpMethod.GET,
                                "/users/v3/api-docs", "/users/v3/api-docs/swagger-config",
                                "/apimgmt/v3/api-docs", "/apimgmt/v3/api-docs/swagger-config",
                                "/customapi/v3/api-docs", "/customapi/v3/api-docs/swagger-config",
                                "/aifeature/v3/api-docs", "/aifeature/v3/api-docs/swagger-config",
                                "/sysmgmt/v3/api-docs", "/sysmgmt/v3/api-docs/swagger-config"
                        ).permitAll()
                        .pathMatchers("/test/health").permitAll()  // Health check는 인증 불필요
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()  // CORS preflight 요청 허용
                        // 유저 서비스 라우팅은 인증 필요
                        .pathMatchers("/gateway/user/**").authenticated()
                        // 기타 보호된 서비스들도 인증 필요
                        .pathMatchers("/gateway/**", "/api/**").authenticated()
                        // 나머지 경로는 허용
                        .anyExchange().permitAll()
                )
                // OAuth2 Login 설정 (Auth0 로그인용)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationRequestResolver(authorizationRequestResolver(clientRegistrationRepository))
                        .authenticationSuccessHandler((exchange, authentication) -> {
                            exchange.getExchange().getResponse().getHeaders().setLocation(
                                java.net.URI.create("/auth/login-success"));
                            exchange.getExchange().getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.FOUND);
                            return Mono.empty();
                        })
                        .authenticationFailureHandler((exchange, exception) -> {
                            exchange.getExchange().getResponse().getHeaders().setLocation(
                                java.net.URI.create("/auth/login-error"));
                            exchange.getExchange().getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.FOUND);
                            return Mono.empty();
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                )
                // OAuth2 Resource Server 설정 (JWT 토큰 검증)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                        // 인증 실패 시 JSON 응답 반환 (브라우저 팝업 방지)
                        .authenticationEntryPoint((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            response.getHeaders().set("Content-Type", "application/json");
                            
                            String jsonResponse = "{\"error\":\"unauthorized\",\"message\":\"JWT token required\",\"status\":401}";
                            var buffer = response.bufferFactory().wrap(jsonResponse.getBytes());
                            return response.writeWith(Mono.just(buffer));
                        })
                        // Access Denied 처리 (권한 부족)
                        .accessDeniedHandler((exchange, denied) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                            response.getHeaders().set("Content-Type", "application/json");
                            
                            String jsonResponse = "{\"error\":\"access_denied\",\"message\":\"Insufficient permissions\",\"status\":403}";
                            var buffer = response.bufferFactory().wrap(jsonResponse.getBytes());
                            return response.writeWith(Mono.just(buffer));
                        })
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
        // permissions -> as-is
        JwtGrantedAuthoritiesConverter permissionsConverter = new JwtGrantedAuthoritiesConverter();
        permissionsConverter.setAuthoritiesClaimName("permissions");
        permissionsConverter.setAuthorityPrefix("");

        // roles -> ROLE_*
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        Converter<Jwt, Flux<GrantedAuthority>> reactiveAuthoritiesConverter = jwt ->
                Flux.fromIterable(permissionsConverter.convert(jwt))
                        .concatWith(Flux.fromIterable(rolesConverter.convert(jwt)));

        ReactiveJwtAuthenticationConverter jwtConverter = new ReactiveJwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(reactiveAuthoritiesConverter);

        return jwtConverter;
    }

    /**
     * OIDC 로그아웃 핸들러를 Bean으로 등록
     */
    @Bean
    public OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedServerLogoutSuccessHandler successHandler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        successHandler.setPostLogoutRedirectUri(logoutRedirectUri);
        return successHandler;
    }

    /**
     * OAuth2 Authorization Request Resolver 설정
     */
    @Bean
    public ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        DefaultServerOAuth2AuthorizationRequestResolver resolver = 
            new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
        
        resolver.setAuthorizationRequestCustomizer(customizer -> {
            customizer.additionalParameters(params -> {
                params.put("audience", audience);
            });
        });
        
        return resolver;
    }

}