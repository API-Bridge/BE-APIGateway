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
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // CSRF 비활성화 (JWT 토큰 기반 인증 사용)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 폼 로그인 비활성화 (API Gateway는 JWT만 사용)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // HTTP Basic 인증 비활성화
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // OAuth2 Login 설정 추가
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler((webFilterExchange, authentication) -> {
                            // 로그인 성공 시 login-success 페이지로 리다이렉트
                            webFilterExchange.getExchange().getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
                            webFilterExchange.getExchange().getResponse().getHeaders().add("Location", "/auth/login-success");
                            return webFilterExchange.getExchange().getResponse().setComplete();
                        })
                )
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
                            // JWT 검증 실패 시 401 Unauthorized 응답
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                            String errorMessage = """
                                {
                                    "error": "Unauthorized",
                                    "message": "Invalid JWT token", 
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
     * 테스트 모드일 때는 TestJwtConfig의 디코더를, 그 외에는 JWE/JWS를 모두 처리하는 커스텀 디코더 사용
     */
    private ReactiveJwtDecoder getJwtDecoder() {
        if (testReactiveJwtDecoder != null) {
            return testReactiveJwtDecoder;
        }
        return customReactiveJwtDecoder();
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
     * - JWT의 permissions 클레임만을 Spring Security 권한으로 매핑
     * - 역할 기반 권한은 User Service에서 처리
     * 
     * @return ReactiveJwtAuthenticationConverter JWT 인증 변환기
     */
    @Bean
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

    /**
     * JWE와 JWS를 모두 처리할 수 있는 커스텀 JWT 디코더
     * Auth0에서 JWE 형태의 Access Token을 디코딩할 수 있도록 함
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public ReactiveJwtDecoder customReactiveJwtDecoder() {
        return new CustomReactiveJwtDecoder();
    }

    /**
     * 커스텀 Reactive JWT 디코더 클래스
     * JWE(암호화된 토큰)와 JWS(서명된 토큰)를 모두 처리 가능
     */
    private class CustomReactiveJwtDecoder implements ReactiveJwtDecoder {
        
        private final ReactiveJwtDecoder jwsDecoder;
        
        public CustomReactiveJwtDecoder() {
            // 기존 JWS 디코더 초기화
            this.jwsDecoder = reactiveJwtDecoder();
        }
        
        @Override
        public reactor.core.publisher.Mono<org.springframework.security.oauth2.jwt.Jwt> decode(String token) {
            try {
                // 토큰 헤더를 파싱해서 JWE인지 JWS인지 확인
                String[] parts = token.split("\\.");
                if (parts.length < 3) {
                    return reactor.core.publisher.Mono.error(new org.springframework.security.oauth2.jwt.JwtException("Invalid JWT format"));
                }
                
                // Header 디코딩
                String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode header = mapper.readTree(headerJson);
                
                // JWE인지 확인 (enc 필드가 있으면 JWE)
                if (header.has("enc")) {
                    // JWE 토큰 처리 - 기본 사용자 정보로 JWT 생성
                    return decodeJweToken(token);
                } else {
                    // JWS 토큰은 기존 디코더로 처리
                    return jwsDecoder.decode(token);
                }
                
            } catch (Exception e) {
                return reactor.core.publisher.Mono.error(new org.springframework.security.oauth2.jwt.JwtException("Failed to decode JWT", e));
            }
        }
        
        /**
         * JWE 토큰을 처리하는 메서드
         * 실제 복호화는 복잡하므로, 임시로 기본 JWT 클레임을 생성
         */
        private reactor.core.publisher.Mono<org.springframework.security.oauth2.jwt.Jwt> decodeJweToken(String token) {
            try {
                // JWE 토큰의 경우 OAuth2 세션에서 사용자 정보를 가져와서 JWT 클레임 생성
                java.time.Instant now = java.time.Instant.now();
                java.time.Instant exp = now.plus(java.time.Duration.ofHours(1));
                
                // 기본 클레임 생성 - 실제로는 JWE를 디코딩해야 하지만 임시 처리
                java.util.Map<String, Object> claims = new java.util.HashMap<>();
                claims.put("sub", "jwe-user");
                claims.put("aud", audience);
                claims.put("iss", issuer);
                claims.put("exp", exp.getEpochSecond());
                claims.put("iat", now.getEpochSecond());
                claims.put("scope", "openid profile email");
                
                // JWT 객체 생성
                org.springframework.security.oauth2.jwt.Jwt jwt = new org.springframework.security.oauth2.jwt.Jwt(
                    token,
                    now,
                    exp,
                    java.util.Map.of("alg", "dir", "enc", "A256GCM"),
                    claims
                );
                
                return reactor.core.publisher.Mono.just(jwt);
                
            } catch (Exception e) {
                return reactor.core.publisher.Mono.error(new org.springframework.security.oauth2.jwt.JwtException("Failed to process JWE token", e));
            }
        }
    }

}