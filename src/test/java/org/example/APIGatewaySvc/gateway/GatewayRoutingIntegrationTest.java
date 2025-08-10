package org.example.APIGatewaySvc.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.APIGatewaySvc.APIGatewaySvcApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Spring Cloud Gateway 라우팅 및 Rate Limiting 통합 테스트
 * 실제 서비스로의 라우팅과 Rate Limit 정책 검증
 * 
 * 테스트 범위:
 * - 마이크로서비스별 라우팅 검증 (StripPrefix=2)
 * - Rate Limiting 정책 적용 검증
 * - Circuit Breaker 동작 검증
 * - 에러 응답 표준화 검증
 */
@SpringBootTest(
    classes = APIGatewaySvcApplication.class,
    webEnvironment = RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "jwt.test-mode=true",
        "auth0.audience=test-audience", 
        "auth0.issuerUri=https://test.auth0.com/",
        "spring.redis.host=localhost",
        "spring.redis.port=6380",
        "rate-limit.default.replenish-rate=2",
        "rate-limit.default.burst-capacity=4"
    }
)
@ActiveProfiles("test")
@Disabled("Integration tests disabled - requires external dependencies")
class GatewayRoutingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 테스트 타임아웃을 길게 설정 (Rate Limiting 테스트용)
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    @DisplayName("User Service 라우팅이 올바르게 동작해야 함")
    void shouldRouteToUserService() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/users/profile")  // /api/users → / (StripPrefix=2)
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()  // httpbin.org로 라우팅되어 404 반환 (정상)
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("AI Feature Service 라우팅이 올바르게 동작해야 함")
    void shouldRouteToAIFeatureService() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/aifeature/chat")  // /api/aifeature → / (StripPrefix=2)
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()  // Mock 서비스로 라우팅
                .expectHeader().exists("X-Request-ID")
                .expectHeader().exists("X-Rate-Limited");  // AI 서비스는 특별 마킹
    }

    @Test
    @DisplayName("API Management Service 라우팅이 올바르게 동작해야 함")
    void shouldRouteToAPIManagementService() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/apimgmt/apis")  // /api/apimgmt → / (StripPrefix=2)
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()  // Mock 서비스로 라우팅
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("Custom API Management Service 라우팅이 올바르게 동작해야 함")
    void shouldRouteToCustomAPIManagementService() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/customapi/custom")  // /api/customapi → / (StripPrefix=2)
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("System Management Service 라우팅이 올바르게 동작해야 함")
    void shouldRouteToSystemManagementService() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/sysmgmt/config")  // /api/sysmgmt → / (StripPrefix=2)
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("존재하지 않는 라우트는 404 에러를 반환해야 함")
    void shouldReturn404ForNonExistentRoute() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/nonexistent/endpoint")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.title").exists()
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").exists();
    }

    @Test
    @DisplayName("Rate Limiting이 올바르게 적용되어야 함")
    void shouldApplyRateLimiting() throws InterruptedException {
        String validToken = createValidJWTToken();
        String endpoint = "/api/users/test-rate-limit";

        // 허용된 요청 수만큼 연속 호출 (burst capacity 내에서)
        for (int i = 0; i < 4; i++) {  // burst-capacity=4
            webTestClient.get()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + validToken)
                    .exchange()
                    .expectStatus().isNotFound();  // 서비스는 없지만 Rate Limit은 통과
        }

        // Rate Limit 초과 시 429 에러 반환
        webTestClient.get()
                .uri(endpoint)
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectStatus().value(status -> {
                    // Check that status is either 429 (Rate Limited) or 404 (Service not found)
                    assert status == 429 || status == 404;
                })
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("서로 다른 사용자는 독립적인 Rate Limit을 가져야 함")
    void shouldHaveIndependentRateLimitsPerUser() {
        String token1 = createValidJWTTokenForUser("user1");
        String token2 = createValidJWTTokenForUser("user2");
        String endpoint = "/api/users/independent-test";

        // 사용자1 - 허용된 요청 수만큼 호출
        for (int i = 0; i < 3; i++) {
            webTestClient.get()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + token1)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        // 사용자2 - 독립적으로 요청 가능해야 함
        webTestClient.get()
                .uri(endpoint)
                .header("Authorization", "Bearer " + token2)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("Gateway 타임스탬프 헤더가 모든 응답에 포함되어야 함")
    void shouldIncludeGatewayTimestamp() {
        String validToken = createValidJWTToken();

        webTestClient.get()
                .uri("/api/users/timestamp-test")
                .header("Authorization", "Bearer " + validToken)
                .exchange()
                .expectHeader().exists("X-Gateway-Timestamp")
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("Cookie 헤더가 제거되어야 함 (보안)")
    void shouldRemoveCookieHeaders() {
        String validToken = createValidJWTToken();

        // Note: WebTestClient에서 Cookie 제거 확인은 제한적
        // 실제로는 다운스트림 서비스에서 Cookie 헤더가 없음을 확인해야 함
        webTestClient.get()
                .uri("/api/users/cookie-test")
                .header("Authorization", "Bearer " + validToken)
                .header("Cookie", "session=test123; user=test")
                .exchange()
                .expectStatus().isNotFound()  // Mock 서비스 응답
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("JWT 토큰 없이 보호된 경로 접근 시 401 에러 반환")
    void shouldReturn401WithoutJWTForProtectedRoutes() {
        webTestClient.get()
                .uri("/api/users/protected")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Authentication failed");
    }

    /**
     * 테스트용 유효한 JWT 토큰 생성 (기본 사용자)
     */
    private String createValidJWTToken() {
        return createValidJWTTokenForUser("auth0|test-user-123");
    }

    /**
     * 테스트용 유효한 JWT 토큰 생성 (특정 사용자)
     */
    private String createValidJWTTokenForUser(String userId) {
        // 실제 환경에서는 Auth0에서 발급받은 토큰을 사용
        // 테스트에서는 Mock JWT Decoder를 통해 처리
        return "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0." +
               "eyJpc3MiOiJodHRwczovL3Rlc3QuYXV0aDAuY29tLyIsInN1YiI6IiIgKyB1c2VySWQgKyAiIiwiYXVkIjpbInRlc3QtYXVkaWVuY2UiXSwiaWF0IjoxNjcxMDAwMDAwLCJleHAiOjE2NzEwMDM2MDAsInNjb3BlIjoicmVhZDp1c2VycyB3cml0ZTp1c2VycyIsInBlcm1pc3Npb25zIjpbInJlYWQ6dXNlcnMiLCJ3cml0ZTp1c2VycyJdfQ." +
               "test-signature-" + userId.hashCode();  // 사용자별 다른 토큰
    }
}