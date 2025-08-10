package org.example.APIGatewaySvc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.APIGatewaySvc.APIGatewaySvcApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Spring Cloud Gateway Security 설정 통합 테스트
 * Auth0 JWT 인증/인가 기능 및 에러 응답 검증
 * 
 * 테스트 범위:
 * - JWT 토큰 검증 (유효/만료/오디언스 불일치/서명 오류)
 * - 공개 경로 접근 허용 검증
 * - 보호된 경로 접근 제어 검증
 * - RFC 7807 Problem Details 에러 응답 검증
 * - Request ID 추적 기능 검증
 */
@SpringBootTest(
    classes = APIGatewaySvcApplication.class,
    webEnvironment = RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "auth0.audience=test-audience",
        "auth0.issuerUri=https://test.auth0.com/",
        "spring.redis.host=localhost",
        "spring.redis.port=6380"  // 테스트용 포트
    }
)
@ActiveProfiles("test")
@Disabled("Security integration tests disabled - requires external dependencies")
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    private ObjectMapper objectMapper;

    // 테스트용 JWT 토큰 데이터
    private static final String TEST_AUDIENCE = "test-audience";
    private static final String TEST_ISSUER = "https://test.auth0.com/";
    private static final String TEST_USER_ID = "auth0|test-user-123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("공개 경로는 인증 없이 접근 가능해야 함")
    void publicEndpointsShouldBeAccessibleWithoutAuthentication() {
        // /public/** 경로 테스트
        webTestClient.get()
                .uri("/public/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.service").isEqualTo("api-gateway")
                .jsonPath("$.status").isEqualTo("UP");

        // /actuator/** 경로 테스트
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        // Swagger UI 접근 테스트
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("보호된 경로는 JWT 토큰 없이 접근 시 401 에러 반환")
    void protectedEndpointsShouldReturn401WithoutJWT() {
        webTestClient.get()
                .uri("/api/users/profile")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.title").isEqualTo("Authentication failed")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").exists()
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 보호된 경로 접근 시 라우팅 됨")
    void validJWTShouldAllowAccessToProtectedRoutes() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/users/profile")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()  // 실제 서비스가 없으므로 404, 하지만 인증은 통과
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("만료된 JWT 토큰은 401 에러 반환")
    void expiredJWTShouldReturn401() {
        String expiredToken = createExpiredJWTToken();

        webTestClient.get()
                .uri("/api/users/profile")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Authentication failed")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("잘못된 오디언스의 JWT 토큰은 401 에러 반환")
    void invalidAudienceJWTShouldReturn401() {
        String invalidAudienceToken = createInvalidAudienceJWTToken();

        webTestClient.get()
                .uri("/api/users/profile")
                .header("Authorization", "Bearer " + invalidAudienceToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Authentication failed")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").value(s -> s.toString().contains("audience"));
    }

    @Test
    @DisplayName("잘못된 형식의 JWT 토큰은 401 에러 반환")
    void malformedJWTShouldReturn401() {
        String malformedToken = "invalid.jwt.token";

        webTestClient.get()
                .uri("/api/users/profile")
                .header("Authorization", "Bearer " + malformedToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Authentication failed")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    @DisplayName("CORS 설정이 올바르게 적용되어야 함")
    void corsShouldBeConfiguredCorrectly() {
        webTestClient.options()
                .uri("/api/users/profile")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Access-Control-Allow-Origin")
                .expectHeader().exists("Access-Control-Allow-Methods")
                .expectHeader().exists("Access-Control-Allow-Headers");
    }

    @Test
    @DisplayName("Request ID가 모든 응답에 포함되어야 함")
    void requestIdShouldBeIncludedInAllResponses() {
        // 성공 응답
        webTestClient.get()
                .uri("/public/health")
                .exchange()
                .expectHeader().exists("X-Request-ID");

        // 에러 응답
        webTestClient.get()
                .uri("/api/users/profile")
                .exchange()
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("민감한 정보가 에러 응답에 포함되지 않아야 함")
    void sensitiveInformationShouldNotBeExposedInErrorResponse() {
        String tokenWithSensitiveInfo = createValidJWTToken();

        webTestClient.get()
                .uri("/api/nonexistent")
                .header("Authorization", "Bearer " + tokenWithSensitiveInfo)
                .exchange()
                .expectStatus().is4xxClientError()
                .expectBody()
                .jsonPath("$.detail").value(detail -> {
                    String detailStr = detail.toString();
                    // JWT 토큰이 마스킹되었는지 확인
                    assert !detailStr.contains(tokenWithSensitiveInfo);
                    // Bearer 토큰 패턴이 마스킹되었는지 확인
                    assert detailStr.contains("[REDACTED]") || !detailStr.contains("Bearer");
                });
    }

    /**
     * 테스트용 유효한 JWT 토큰 생성 (Mock)
     */
    private String createValidJWTToken() {
        // 실제 환경에서는 Auth0에서 발급받은 토큰을 사용
        // 테스트에서는 Mock JWT Decoder를 통해 처리
        return "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0." +
               "eyJpc3MiOiJodHRwczovL3Rlc3QuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfHRlc3QtdXNlci0xMjMiLCJhdWQiOlsidGVzdC1hdWRpZW5jZSJdLCJpYXQiOjE2NzEwMDAwMDAsImV4cCI6MTY3MTAwMzYwMCwic2NvcGUiOiJyZWFkOnVzZXJzIHdyaXRlOnVzZXJzIiwicGVybWlzc2lvbnMiOlsicmVhZDp1c2VycyIsIndyaXRlOnVzZXJzIl19." +
               "test-signature";
    }

    /**
     * 테스트용 만료된 JWT 토큰 생성 (Mock)
     */
    private String createExpiredJWTToken() {
        return "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0." +
               "eyJpc3MiOiJodHRwczovL3Rlc3QuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfHRlc3QtdXNlci0xMjMiLCJhdWQiOlsidGVzdC1hdWRpZW5jZSJdLCJpYXQiOjE2NzA5OTAwMDAsImV4cCI6MTY3MDk5MzYwMCwic2NvcGUiOiJyZWFkOnVzZXJzIiwicGVybWlzc2lvbnMiOlsicmVhZDp1c2VycyJdfQ." +
               "expired-signature";
    }

    /**
     * 테스트용 잘못된 오디언스 JWT 토큰 생성 (Mock)
     */
    private String createInvalidAudienceJWTToken() {
        return "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0." +
               "eyJpc3MiOiJodHRwczovL3Rlc3QuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfHRlc3QtdXNlci0xMjMiLCJhdWQiOlsid3JvbmctYXVkaWVuY2UiXSwiaWF0IjoxNjcxMDAwMDAwLCJleHAiOjE2NzEwMDM2MDAsInNjb3BlIjoicmVhZDp1c2VycyIsInBlcm1pc3Npb25zIjpbInJlYWQ6dXNlcnMiXX0." +
               "invalid-audience-signature";
    }
}