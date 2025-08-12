package org.example.APIGatewaySvc.config;

import org.example.APIGatewaySvc.security.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import reactor.core.publisher.Flux;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.util.Map;

// import java.time.Duration; // not used

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

    // 테스트 모드일 때 TestJwtConfig의 디코더를 주입받기 위함
    @Autowired(required = false)
    private ReactiveJwtDecoder testReactiveJwtDecoder;

    // OAuth2 클라이언트 등록 정보 저장소
    // OIDC 로그아웃 핸들러에서 사용됨
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Spring Security WebFlux 필터 체인 설정
     * - JWT 기반 인증을 위한 OAuth2 Resource Server 설정
     * - 공개 경로와 보호된 경로 구분
     * - Stateless 인증 (세션 비활성화)
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler) {
        return http
                // CSRF 비활성화 (JWT 토큰 기반 인증 사용)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 폼 로그인 비활성화 (API Gateway는 JWT만 사용)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // HTTP Basic 인증 비활성화
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // OAuth2 Login용 세션은 활성화, JWT 검증은 stateless 유지
                // .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // 경로별 접근 권한 설정
                .authorizeExchange(exchanges -> exchanges
// --- 모든 permitAll() 경로를 여기에 모아두어야 합니다. ---
                                // Auth0 로그인/로그아웃 관련 경로를 가장 먼저 허용
                                .pathMatchers("/auth/**").permitAll()
                                .pathMatchers("/oauth2/**").permitAll()
                                .pathMatchers("/login/**").permitAll()
                                .pathMatchers("/public/**").permitAll()

                                // Swagger 관련 경로를 모두 허용
                                .pathMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                                .pathMatchers("/v3/api-docs/**", "/v3/api-docs", "/v3/api-docs.yaml").permitAll()
                                .pathMatchers("/webjars/**").permitAll()
                                .pathMatchers("/swagger-resources/**").permitAll()
                                .pathMatchers("/configuration/**").permitAll()
                                .pathMatchers("/swagger-config.json").permitAll()
                                .pathMatchers("/api-docs/**").permitAll()

                                // Gateway를 통한 각 서비스의 OpenAPI 문서 경로도 허용
                                .pathMatchers(HttpMethod.GET,
                                        "/gateway/users/v3/api-docs", "/gateway/users/v3/api-docs/swagger-config",
                                        "/gateway/apimgmt/v3/api-docs", "/gateway/apimgmt/v3/api-docs/swagger-config",
                                        "/gateway/customapi/v3/api-docs", "/gateway/customapi/v3/api-docs/swagger-config",
                                        "/gateway/aifeature/v3/api-docs", "/gateway/aifeature/v3/api-docs/swagger-config",
                                        "/gateway/sysmgmt/v3/api-docs", "/gateway/sysmgmt/v3/api-docs/swagger-config"
                                ).permitAll()

                                // 기타 공개 경로들
                                .pathMatchers("/favicon.ico").permitAll()
                                .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                                .pathMatchers("/test/health").permitAll()
                                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                                .pathMatchers("/error").permitAll()
                                .pathMatchers("/").permitAll()

                                // --- 관리자만 접근 가능한 actuator 경로는 permitAll()보다 뒤에 위치 ---
                                .pathMatchers(HttpMethod.GET, "/actuator/**").hasRole("ADMIN")
                                .pathMatchers("/actuator/**").denyAll()

                                // --- 나머지 모든 요청은 인증 필요 (가장 마지막에 배치) ---
                                .anyExchange().authenticated()
                )
                // OAuth2 Login 설정 활성화
                .oauth2Login(oauth2 -> oauth2
                        // OAuth2 로그인 성공/실패 핸들러 설정
                        // 로그인 성공 시 /auth/login-success로 리다이렉트
                        .authenticationSuccessHandler((exchange, authentication) -> {
                            exchange.getExchange().getResponse().getHeaders().setLocation(
                                java.net.URI.create("/auth/login-success"));
                            exchange.getExchange().getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.FOUND);
                            return Mono.empty();
                        })
                        // 로그인 실패 시 /auth/login-error로 리다이렉트
                        .authenticationFailureHandler((exchange, exception) -> {
                            exchange.getExchange().getResponse().getHeaders().setLocation(
                                java.net.URI.create("/auth/login-error"));
                            exchange.getExchange().getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.FOUND);
                            return reactor.core.publisher.Mono.empty();
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout") // 로그아웃 URL을 명시
                        .logoutSuccessHandler(oidcLogoutSuccessHandler) // <-- Bean으로 등록한 핸들러 사용
                )

                // OAuth2 Resource Server JWT 검증 설정
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(getJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                        .authenticationEntryPoint((exchange, ex) -> {
                            // JWT 검증 실패 시 401 Unauthorized 응답
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            String errorMessage = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}";
                            org.springframework.core.io.buffer.DataBuffer buffer = 
                                exchange.getResponse().bufferFactory().wrap(errorMessage.getBytes());
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                        })
                )
                .build();
    }

    /**
     * OIDC 로그아웃 핸들러를 Bean으로 등록
     * - Auth0를 통해 로그아웃하고 지정된 URI로 리다이렉션
     * - ReactiveClientRegistrationRepository를 생성자로 받아야 함
     */
    @Bean
    public OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedServerLogoutSuccessHandler successHandler =
                new OidcClientInitiatedServerLogoutSuccessHandler(this.clientRegistrationRepository);
        successHandler.setPostLogoutRedirectUri(String.valueOf(java.net.URI.create("/")));
        return successHandler;
    }

    /**
     * JWT 디코더 결정 로직
     * 테스트 모드일 때는 TestJwtConfig의 디코더를, 그 외에는 Auth0 디코더 사용
     */
    private ReactiveJwtDecoder getJwtDecoder() {
        if (testReactiveJwtDecoder != null) {
            return testReactiveJwtDecoder;
        }
        return reactiveJwtDecoder();
    }

    /**
     * Reactive JWT Decoder 설정 (Auth0용)
     * - Auth0 JWKS 엔드포인트에서 공개키를 가져와 JWT 서명 검증
     * - Audience 검증을 통한 토큰 적합성 확인
     * - 네트워크 타임아웃 및 캐시 설정으로 성능 최적화
     * 
     * @return ReactiveJwtDecoder JWT 토큰 디코더
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        // issuer 포맷 보정: 끝의 슬래시 보장
        String normalizedIssuer = issuer.endsWith("/") ? issuer : issuer + "/";

        // Auth0 JWKS 엔드포인트에서 JWT 디코더 생성 (RS256)
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(normalizedIssuer + ".well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // JWT 토큰 검증 설정
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(normalizedIssuer);
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

}