package org.example.APIGatewaySvc.config;

import org.example.APIGatewaySvc.security.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
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
 * Spring Cloud Gateway Reactive Security 설정 클래스 - 완전 비활성화
 * NewSecurityConfig로 교체되어 더 이상 사용하지 않음
 *
 * 이 클래스는 OAuth2 Resource Server를 사용하여 JWT 검증을 중복으로 수행하는 문제를 방지하기 위해
 * 완전히 비활성화되었습니다. 현재는 CustomJwtAuthenticationFilter를 사용하는 NewSecurityConfig만 활성화됩니다.
 */
// 완전 비활성화 - NewSecurityConfig 사용으로 인해 JWT 중복 검증 방지
// @Configuration
// @EnableWebFluxSecurity
public class SecurityConfig {

    // Auth0 API Identifier를 audience로 직접 설정 (Client ID도 허용)
    private String audience = "https://api.api-bridge.com";

    @Value("${auth0.issuerUri}")
    private String issuer;

    // logout-redirect-uri는 User Service에서 처리하므로 API Gateway에서는 불필요
    // @Value("${auth0.logout-redirect-uri}")
    // private String logoutRedirectUri;

    // 테스트 모드일 때 TestJwtConfig의 디코더를 주입받기 위함
    @Autowired(required = false)
    private ReactiveJwtDecoder testReactiveJwtDecoder;

    // *** OAuth2 Client 의존성 주석 처리됨 - User Service로 Auth0 로그인 처리 이전 ***
    // OAuth2 클라이언트 등록 정보 저장소
    // OIDC 로그아웃 핸들러에서 사용됨
    // private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(/* ReactiveClientRegistrationRepository clientRegistrationRepository */) {
        // this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Spring Security WebFlux 필터 체인 설정 - 비활성화
     * NewSecurityConfig로 교체되어 더 이상 사용하지 않음
     */
    // @Bean  // JWT 중복 검증 방지를 위해 비활성화
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // CSRF 비활성화 (JWT 토큰 기반 인증 사용)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 폼 로그인 비활성화 (API Gateway는 JWT만 사용)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // HTTP Basic 인증 비활성화
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // Stateless 인증 - 세션 사용하지 않음
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // *** OAuth2 Login 설정 주석 처리됨 - User Service로 Auth0 로그인 처리 이전 ***
                // Auth0 OAuth2 로그인이 User Service로 이전되었습니다.
                // 롤백이 필요한 경우 아래 주석을 해제하세요.
                // .oauth2Login(oauth2 -> oauth2
                //         .authenticationSuccessHandler((webFilterExchange, authentication) -> {
                //             // 로그인 성공 시 login-success 페이지로 리다이렉트
                //             webFilterExchange.getExchange().getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
                //             webFilterExchange.getExchange().getResponse().getHeaders().add("Location", "/auth/login-success");
                //             return webFilterExchange.getExchange().getResponse().setComplete();
                //         })
                // )
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
                                .pathMatchers("/test/**").permitAll()      // 모든 테스트 엔드포인트 허용
                                .pathMatchers("/mock/**").permitAll()      // 모든 목업 마이크로서비스 허용
                                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                                .pathMatchers("/error").permitAll()
                                .pathMatchers("/").permitAll()
                                
                                // 모니터링 Actuator 엔드포인트들 - 공개 접근 허용
                                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                                .pathMatchers("/actuator/metrics", "/actuator/metrics/**").permitAll()
                                .pathMatchers("/actuator/prometheus").permitAll()
                                .pathMatchers("/actuator/gateway", "/actuator/gateway/**").permitAll()
                                .pathMatchers("/actuator/circuitbreakers", "/actuator/circuitbreakers/**").permitAll()
                                
                                // Gateway API 경로들 - OAuth2 세션 인증으로 임시 변경
                                // HEAD 요청은 허용 (브라우저 preflight 및 존재 여부 확인용)
                                .pathMatchers(HttpMethod.HEAD, "/gateway/users/**").permitAll()
                                // 로그인 관련 엔드포인트는 인증 없이 접근 허용
                                .pathMatchers("/gateway/users/api/auth/login", "/gateway/users/api/auth/register").permitAll()
                                .pathMatchers("/gateway/users/**").authenticated()
                                .pathMatchers("/gateway/apimgmt/**").authenticated()
                                .pathMatchers("/gateway/customapi/**").authenticated()

                                // --- 관리자 경로들 - JWT 토큰 인증 필요 (권한은 User Service에서 검증) ---
                                .pathMatchers("/admin/**").authenticated()
                                .pathMatchers("/gateway/users/admin/**").authenticated()
                                .pathMatchers("/gateway/admin/**").authenticated()
                                .pathMatchers("/gateway/apimgmt/admin/**").authenticated()
                                .pathMatchers("/gateway/customapi/admin/**").authenticated()
                                .pathMatchers("/gateway/monitoring/**").authenticated()
                                
                                // --- 민감한 actuator 경로 - 관리자 전용 (JWT 토큰 인증 필요) ---
                                .pathMatchers("/actuator/env", "/actuator/env/**").authenticated()
                                .pathMatchers("/actuator/beans", "/actuator/configprops").authenticated()
                                .pathMatchers("/actuator/shutdown", "/actuator/restart").authenticated()

                                // --- 나머지 모든 요청은 인증 필요 (가장 마지막에 배치) ---
                                .anyExchange().authenticated()
                )
                // 인증 실패 시 JSON 오류 응답 반환 (리다이렉트 대신)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            // 인증 실패 시 401 Unauthorized 응답
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            String errorMessage = """
                                {
                                    "error": "Unauthorized",
                                    "message": "Authentication required. Please provide a valid JWT token.",
                                    "status": 401,
                                    "timestamp": "%s",
                                    "path": "%s"
                                }
                                """.formatted(
                                    java.time.Instant.now().toString(),
                                    exchange.getRequest().getURI().getPath()
                                );
                            org.springframework.core.io.buffer.DataBuffer buffer = 
                                exchange.getResponse().bufferFactory().wrap(errorMessage.getBytes());
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                        })
                        .accessDeniedHandler((exchange, denied) -> {
                            // 권한 부족 시 403 Forbidden 응답
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            String errorMessage = """
                                {
                                    "error": "Forbidden",
                                    "message": "Access denied. Insufficient permissions.",
                                    "status": 403,
                                    "timestamp": "%s",
                                    "path": "%s"
                                }
                                """.formatted(
                                    java.time.Instant.now().toString(),
                                    exchange.getRequest().getURI().getPath()
                                );
                            org.springframework.core.io.buffer.DataBuffer buffer = 
                                exchange.getResponse().bufferFactory().wrap(errorMessage.getBytes());
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                        })
                )

                // OAuth2 Resource Server JWT 검증 설정
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(getJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                        .authenticationEntryPoint((exchange, ex) -> {
                            // JWT 검증 실패 시 401 Unauthorized 응답 + 상세 디버깅
                            String requestPath = exchange.getRequest().getURI().getPath();
                            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                            
                            System.out.println("=== JWT 인증 실패 디버깅 ===");
                            System.out.println("요청 경로: " + requestPath);
                            System.out.println("Authorization 헤더 존재: " + (authHeader != null));
                            if (authHeader != null) {
                                System.out.println("Authorization 헤더 길이: " + authHeader.length());
                                System.out.println("토큰 시작: " + (authHeader.length() > 20 ? authHeader.substring(0, 30) + "..." : authHeader));
                            }
                            System.out.println("예외 유형: " + ex.getClass().getSimpleName());
                            System.out.println("예외 메시지: " + ex.getMessage());
                            if (ex.getCause() != null) {
                                System.out.println("원인: " + ex.getCause().getClass().getSimpleName());
                                System.out.println("원인 메시지: " + ex.getCause().getMessage());
                            }
                            System.out.println("==========================");
                            
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            String errorMessage = """
                                {
                                    "error": "Unauthorized",
                                    "message": "Invalid JWT token: %s", 
                                    "status": 401,
                                    "timestamp": "%s",
                                    "path": "%s"
                                }
                                """.formatted(
                                    ex.getMessage(),
                                    java.time.Instant.now().toString(),
                                    requestPath
                                );
                            org.springframework.core.io.buffer.DataBuffer buffer = 
                                exchange.getResponse().bufferFactory().wrap(errorMessage.getBytes());
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(buffer));
                        })
                )
                .build();
    }

    /**
     * OIDC 로그아웃 핸들러 - 현재 사용하지 않음
     * AuthController에서 직접 Auth0 로그아웃 URL로 리다이렉트 처리
     */
    // @Bean
    // public OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
    //     OidcClientInitiatedServerLogoutSuccessHandler successHandler =
    //             new OidcClientInitiatedServerLogoutSuccessHandler(this.clientRegistrationRepository);
    //     // 로그아웃 후 리다이렉트할 URI 설정
    //     successHandler.setPostLogoutRedirectUri(logoutRedirectUri);
    //     return successHandler;
    // }

    /**
     * JWT 디코더 결정 로직
     * 테스트 모드일 때는 TestJwtConfig의 디코더를, 그 외에는 기본 JWS 디코더 사용
     */
    private ReactiveJwtDecoder getJwtDecoder() {
        if (testReactiveJwtDecoder != null) {
            return testReactiveJwtDecoder;
        }
        return reactiveJwtDecoder();
    }

    /**
     * Reactive JWT Decoder 설정 - 비활성화
     * NewSecurityConfig로 교체되어 더 이상 사용하지 않음
     */
    // @Bean  // JWT 중복 검증 방지를 위해 비활성화
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        // issuer 포맷 보정: 끝의 슬래시 보장
        String normalizedIssuer = issuer.endsWith("/") ? issuer : issuer + "/";

        System.out.println("=== API Gateway JWT 디코더 초기화 ===");
        System.out.println("Issuer: " + normalizedIssuer);
        System.out.println("JWKS URI: " + normalizedIssuer + ".well-known/jwks.json");
        System.out.println("Audience: " + audience);
        System.out.println("====================================");

        // Auth0 JWKS 엔드포인트에서 JWT 디코더 생성 (RS256)
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(normalizedIssuer + ".well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // JWT 토큰 검증 설정 - ID 토큰도 허용하도록 Audience 검증 완화
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(normalizedIssuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    /**
     * JWT 토큰 인증 컨버터 - 비활성화
     * NewSecurityConfig로 교체되어 더 이상 사용하지 않음
     */
    // @Bean  // JWT 중복 검증 방지를 위해 비활성화
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        // permissions -> as-is
        JwtGrantedAuthoritiesConverter permissionsConverter = new JwtGrantedAuthoritiesConverter();
        permissionsConverter.setAuthoritiesClaimName("permissions");
        permissionsConverter.setAuthorityPrefix("");

        // 역할 기반 권한 검증 제거 - User Service에서 관리자 판별
        Converter<Jwt, Flux<GrantedAuthority>> reactiveAuthoritiesConverter = jwt ->
                Flux.fromIterable(permissionsConverter.convert(jwt));

        ReactiveJwtAuthenticationConverter jwtConverter = new ReactiveJwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(reactiveAuthoritiesConverter);

        return jwtConverter;
    }

    // 커스텀 JWE 디코더는 제거 - Auth0 ID 토큰은 JWS 형태이므로 불필요

}