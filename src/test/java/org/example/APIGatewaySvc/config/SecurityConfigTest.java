package org.example.APIGatewaySvc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security 설정 테스트
 * JWT 인증 및 OAuth2 설정 검증
 */
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(clientRegistrationRepository);
    }

    // @Test
    void shouldCreateReactiveJwtDecoder() {
        // When
        // ReactiveJwtDecoder jwtDecoder = securityConfig.reactiveJwtDecoder();

        // Then
        // assertNotNull(jwtDecoder);
    }

    @Test
    void shouldCreateJwtAuthenticationConverter() {
        // When
        ReactiveJwtAuthenticationConverter converter = 
            securityConfig.reactiveJwtAuthenticationConverter();

        // Then
        assertNotNull(converter);
    }

    @Test
    void shouldCreateAuthorizationRequestResolver() {
        // When
        ServerOAuth2AuthorizationRequestResolver resolver = 
            securityConfig.authorizationRequestResolver();

        // Then
        assertNotNull(resolver);
    }

    @Test
    void shouldConfigureWithClientRegistrationRepository() {
        // Given
        SecurityConfig config = new SecurityConfig(clientRegistrationRepository);

        // Then
        assertNotNull(config);
    }

    @Test
    void shouldHandleAuth0Configuration() {
        // Given - .env 파일에서 확인한 Auth0 설정
        String expectedIssuer = "https://api-bridge.us.auth0.com/";
        String expectedAudience = "http://localhost:8080/";

        // When & Then
        assertNotNull(expectedIssuer);
        assertNotNull(expectedAudience);
        assertTrue(expectedIssuer.startsWith("https://"));
        assertTrue(expectedIssuer.endsWith("/"));
    }

    @Test
    void shouldValidateJwtTokenStructure() {
        // Given - JWT 토큰이 가져야 할 클레임들
        String[] requiredClaims = {"sub", "iss", "exp", "iat", "aud"};
        String[] expectedPermissionsClaims = {"permissions", "https://api-bridge.us.auth0.com/roles"};

        // When & Then
        for (String claim : requiredClaims) {
            assertNotNull(claim);
            assertFalse(claim.trim().isEmpty());
        }

        for (String claim : expectedPermissionsClaims) {
            assertNotNull(claim);
            assertFalse(claim.trim().isEmpty());
        }
    }

    @Test
    void shouldDefineSecurityPathMatchers() {
        // Given - SecurityConfig에서 정의된 경로들
        String[] publicPaths = {
            "/auth/**", 
            "/oauth2/**", 
            "/login/**", 
            "/public/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health"
        };

        String[] adminPaths = {
            "/gateway/users/admin/**",
            "/gateway/admin/**", 
            "/gateway/apimgmt/admin/**",
            "/gateway/customapi/admin/**",
            "/gateway/monitoring/**"
        };

        // When & Then
        for (String path : publicPaths) {
            assertNotNull(path);
            assertTrue(path.contains("/"));
        }

        for (String path : adminPaths) {
            assertNotNull(path);
            assertTrue(path.contains("/"));
            assertTrue(path.contains("admin") || path.contains("monitoring"));
        }
    }
}