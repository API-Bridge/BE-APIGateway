package org.example.APIGatewaySvc.config;

import org.example.APIGatewaySvc.security.CustomJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 새로운 Spring Cloud Gateway Reactive Security 설정
 * - 커스텀 JWT 검증 로직 사용
 * - 향상된 에러 핸들링 및 디버깅
 * - 단순화된 보안 설정
 */
@Configuration
@EnableWebFluxSecurity
public class NewSecurityConfig {
    
    private final CustomJwtAuthenticationFilter customJwtAuthenticationFilter;
    
    public NewSecurityConfig(CustomJwtAuthenticationFilter customJwtAuthenticationFilter) {
        this.customJwtAuthenticationFilter = customJwtAuthenticationFilter;
    }
    
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // CSRF 비활성화 (API Gateway는 Stateless)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                
                // 세션 사용하지 않음 (JWT 토큰 기반 인증)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                
                // CORS 설정
                .cors(cors -> cors.configurationSource(request -> {
                    org.springframework.web.cors.CorsConfiguration configuration = 
                        new org.springframework.web.cors.CorsConfiguration();
                    configuration.setAllowCredentials(true);
                    // 특정 origin 명시적 허용 (credentials=true일 때 *는 불가)
                    configuration.addAllowedOrigin("http://localhost:9002");
                    configuration.addAllowedOrigin("http://localhost:3000");
                    configuration.addAllowedOrigin("http://localhost:8080");
                    configuration.addAllowedHeader("*");
                    configuration.addAllowedMethod("*");
                    return configuration;
                }))
                
                // HTTP 보안 헤더는 기본값 사용
                
                // 경로별 인증 설정
                .authorizeExchange(exchanges -> exchanges
                    // Actuator 경로는 최우선으로 permitAll
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/", "/favicon.ico").permitAll()
                    .pathMatchers("/public/**").permitAll()
                    .pathMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .pathMatchers("/v3/api-docs/**", "/v3/api-docs").permitAll()
                    .pathMatchers("/webjars/**").permitAll()
                    .pathMatchers("/swagger-resources/**").permitAll()
                    .pathMatchers("/configuration/**").permitAll()
                    .pathMatchers("/swagger-config.json").permitAll()
                    .pathMatchers("/api-docs/**").permitAll()
                    
                    // Auth 관련 경로 (JWT 검증 제외)
                    .pathMatchers("/auth/**", "/oauth2/**", "/login/**").permitAll()
                    
                    // Gateway별 API 문서 (공개)
                    .pathMatchers(HttpMethod.GET, "/gateway/users/v3/api-docs").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/users/v3/api-docs/swagger-config").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/apimgmt/v3/api-docs").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/apimgmt/v3/api-docs/swagger-config").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/customapi/v3/api-docs").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/customapi/v3/api-docs/swagger-config").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/aifeature/v3/api-docs").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/aifeature/v3/api-docs/swagger-config").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/sysmgmt/v3/api-docs").permitAll()
                    .pathMatchers(HttpMethod.GET, "/gateway/sysmgmt/v3/api-docs/swagger-config").permitAll()
                    
                    
                    // 테스트/개발 경로
                    .pathMatchers("/test/**", "/mock/**").permitAll()
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/error").permitAll()
                    
                    // HEAD 요청 허용
                    .pathMatchers(HttpMethod.HEAD, "/gateway/users/**").permitAll()
                    
                    // 로그인/회원가입 관련 엔드포인트는 인증 불필요 (더 구체적인 경로를 먼저 배치)
                    .pathMatchers("/gateway/users/api/auth/login", "/gateway/users/api/auth/register").permitAll()
                    .pathMatchers("/gateway/users/api/auth/**").permitAll()  // 추가 인증 관련 경로들
                    
                    // 모든 Gateway API는 인증 필요
                    .pathMatchers("/gateway/users/**").authenticated()
                    .pathMatchers("/gateway/apimgmt/**").authenticated()
                    .pathMatchers("/gateway/customapi/**").authenticated()
                    .pathMatchers("/gateway/aifeature/**").authenticated()
                    .pathMatchers("/gateway/sysmgmt/**").authenticated()
                    
                    // 나머지 모든 요청은 인증 필요
                    .anyExchange().authenticated()
                )
                
                // 커스텀 JWT 필터 추가 (Authentication 전에 배치하여 우선 실행)
                .addFilterBefore(customJwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                
                // 기본 인증 방식 비활성화 (OAuth2 Resource Server 사용하지 않음)
                // OAuth2 Resource Server를 완전히 비활성화 (커스텀 JWT 필터 사용)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // OAuth2ResourceServer는 설정하지 않음 (자동 설정 비활성화)
                
                // 인증 실패 시 JSON 응답 반환 (브라우저 기본 인증 창 비활성화)
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((exchange, ex) -> {
                        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                        String errorMessage = """
                            {
                                "error": "Unauthorized",
                                "message": "JWT token required. Please provide a valid Bearer token.",
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
}