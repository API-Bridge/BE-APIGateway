package org.example.APIGatewaySvc.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.example.APIGatewaySvc.util.JwtTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Auth0 JWT 인증 및 API 라우팅 통합 테스트
 * WireMock을 사용하여 다운스트림 서비스를 모킹
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("Auth0 JWT 인증 및 API 라우팅 통합 테스트")
class AuthTestIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTestUtil jwtTestUtil;

    private WireMockServer userServiceMock;
    private WireMockServer apiMgmtServiceMock;
    private WireMockServer customApiServiceMock;
    private WireMockServer aiFeatureServiceMock;
    private WireMockServer sysMgmtServiceMock;

    @BeforeEach
    void setUp() {
        // 각 마이크로서비스에 대한 WireMock 서버 설정
        userServiceMock = new WireMockServer(WireMockConfiguration.options().port(8081));
        apiMgmtServiceMock = new WireMockServer(WireMockConfiguration.options().port(8082));
        customApiServiceMock = new WireMockServer(WireMockConfiguration.options().port(8083));
        aiFeatureServiceMock = new WireMockServer(WireMockConfiguration.options().port(8084));
        sysMgmtServiceMock = new WireMockServer(WireMockConfiguration.options().port(8085));

        // WireMock 서버 시작
        userServiceMock.start();
        apiMgmtServiceMock.start();
        customApiServiceMock.start();
        aiFeatureServiceMock.start();
        sysMgmtServiceMock.start();

        // 기본 Mock 응답 설정
        setupDefaultMockResponses();
    }

    @AfterEach
    void tearDown() {
        // WireMock 서버 중지
        if (userServiceMock.isRunning()) userServiceMock.stop();
        if (apiMgmtServiceMock.isRunning()) apiMgmtServiceMock.stop();
        if (customApiServiceMock.isRunning()) customApiServiceMock.stop();
        if (aiFeatureServiceMock.isRunning()) aiFeatureServiceMock.stop();
        if (sysMgmtServiceMock.isRunning()) sysMgmtServiceMock.stop();
    }

    @Test
    @DisplayName("Auth0 설정 정보 조회 - 인증 없이 접근 가능")
    void testAuth0ConfigEndpoint() {
        webTestClient.get()
                .uri("/public/auth0-config")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.domain").exists()
                .jsonPath("$.clientId").exists()
                .jsonPath("$.audience").exists()
                .consumeWith(result -> {
                    System.out.println("Auth0 Config Response: " + new String(result.getResponseBody()));
                });
    }

    @Test
    @DisplayName("정적 리소스 접근 - auth-test.html 페이지")
    void testStaticResourceAccess() {
        webTestClient.get()
                .uri("/public/auth-test.html")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .consumeWith(result -> {
                    String html = result.getResponseBody();
                    assert html != null;
                    assert html.contains("API Gateway - Auth0 JWT 테스트");
                    assert html.contains("Auth0 로그인");
                });
    }

    @Test
    @DisplayName("JWT 토큰 없이 보호된 엔드포인트 접근 - 401 Unauthorized")
    void testUnauthorizedAccessToProtectedEndpoint() {
        webTestClient.get()
                .uri("/users/profile")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 Users API 접근")
    void testValidJwtTokenToUsersApi() {
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        webTestClient.get()
                .uri("/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.message").isEqualTo("User profile retrieved successfully")
                .jsonPath("$.service").isEqualTo("user-service")
                .consumeWith(result -> {
                    String requestId = result.getResponseHeaders().getFirst("X-Request-ID");
                    assert requestId != null && !requestId.isEmpty();
                    System.out.println("Request ID: " + requestId);
                });
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 API Management API 접근")
    void testValidJwtTokenToApiMgmtApi() {
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        webTestClient.get()
                .uri("/apimgmt/apis")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.message").isEqualTo("API list retrieved successfully")
                .jsonPath("$.service").isEqualTo("api-management-service");
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 Custom API Management API 접근")
    void testValidJwtTokenToCustomApiMgmtApi() {
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        webTestClient.get()
                .uri("/customapi/templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.message").isEqualTo("Templates retrieved successfully")
                .jsonPath("$.service").isEqualTo("custom-api-management-service");
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 AI Feature API 접근")
    void testValidJwtTokenToAiFeatureApi() {
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        webTestClient.get()
                .uri("/aifeature/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.message").isEqualTo("AI models retrieved successfully")
                .jsonPath("$.service").isEqualTo("ai-feature-service");
    }

    @Test
    @DisplayName("유효한 JWT 토큰으로 System Management API 접근")
    void testValidJwtTokenToSysMgmtApi() {
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        webTestClient.get()
                .uri("/sysmgmt/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.message").isEqualTo("System health check successful")
                .jsonPath("$.service").isEqualTo("system-management-service");
    }

    @Test
    @DisplayName("무효한 JWT 토큰으로 API 접근 - 401 Unauthorized")
    void testInvalidJwtToken() {
        String invalidToken = "invalid.jwt.token";
        
        webTestClient.get()
                .uri("/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("만료된 JWT 토큰으로 API 접근 - 401 Unauthorized")
    void testExpiredJwtToken() {
        String expiredToken = jwtTestUtil.generateExpiredToken("test-user@example.com");
        
        webTestClient.get()
                .uri("/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("잘못된 Audience를 가진 JWT 토큰 - 401 Unauthorized")
    void testJwtTokenWithWrongAudience() {
        String wrongAudienceToken = jwtTestUtil.generateTokenWithWrongAudience("test-user@example.com");
        
        webTestClient.get()
                .uri("/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudienceToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("다운스트림 서비스 오류 시 Circuit Breaker 동작 확인")
    void testCircuitBreakerWhenDownstreamServiceFails() {
        // User Service에서 서버 오류 발생하도록 설정
        userServiceMock.stubFor(get(urlPathEqualTo("/profile"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));
        
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        // 여러 번 요청하여 Circuit Breaker 동작 확인
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                    .uri("/users/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    @Test
    @DisplayName("Rate Limiting 동작 확인 - 과도한 요청 시 429 응답")
    void testRateLimiting() {
        String validToken = jwtTestUtil.generateValidToken("test-user@example.com");
        
        // 짧은 시간 내에 많은 요청 전송 (Rate Limit 초과)
        for (int i = 0; i < 25; i++) {
            webTestClient.get()
                    .uri("/users/profile")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange();
        }
        
        // 마지막 요청은 Rate Limit에 걸려야 함
        webTestClient.get()
                .uri("/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(429); // Too Many Requests
    }

    @Test
    @DisplayName("CORS 헤더 확인 - OPTIONS 요청")
    void testCorsHeaders() {
        webTestClient.options()
                .uri("/users/profile")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Access-Control-Allow-Origin")
                .expectHeader().exists("Access-Control-Allow-Methods")
                .expectHeader().exists("Access-Control-Allow-Headers");
    }

    /**
     * 각 마이크로서비스의 기본 Mock 응답 설정
     */
    private void setupDefaultMockResponses() {
        // User Service Mock
        userServiceMock.stubFor(get(urlPathEqualTo("/profile"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "message": "User profile retrieved successfully",
                                    "service": "user-service",
                                    "timestamp": "2024-01-01T00:00:00Z",
                                    "data": {
                                        "userId": "test-user-123",
                                        "email": "test-user@example.com",
                                        "name": "Test User"
                                    }
                                }
                                """)));

        // API Management Service Mock
        apiMgmtServiceMock.stubFor(get(urlPathEqualTo("/apis"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "message": "API list retrieved successfully",
                                    "service": "api-management-service",
                                    "timestamp": "2024-01-01T00:00:00Z",
                                    "data": [
                                        {"id": 1, "name": "User API", "version": "v1"},
                                        {"id": 2, "name": "Payment API", "version": "v2"}
                                    ]
                                }
                                """)));

        // Custom API Management Service Mock
        customApiServiceMock.stubFor(get(urlPathEqualTo("/templates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "message": "Templates retrieved successfully",
                                    "service": "custom-api-management-service",
                                    "timestamp": "2024-01-01T00:00:00Z",
                                    "data": [
                                        {"id": 1, "name": "REST Template", "type": "API"},
                                        {"id": 2, "name": "GraphQL Template", "type": "API"}
                                    ]
                                }
                                """)));

        // AI Feature Service Mock
        aiFeatureServiceMock.stubFor(get(urlPathEqualTo("/models"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "message": "AI models retrieved successfully",
                                    "service": "ai-feature-service",
                                    "timestamp": "2024-01-01T00:00:00Z",
                                    "data": [
                                        {"id": 1, "name": "GPT-4", "provider": "OpenAI"},
                                        {"id": 2, "name": "Claude-3", "provider": "Anthropic"}
                                    ]
                                }
                                """)));

        // System Management Service Mock
        sysMgmtServiceMock.stubFor(get(urlPathEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "message": "System health check successful",
                                    "service": "system-management-service",
                                    "timestamp": "2024-01-01T00:00:00Z",
                                    "data": {
                                        "status": "UP",
                                        "components": {
                                            "database": "UP",
                                            "redis": "UP",
                                            "external-api": "UP"
                                        }
                                    }
                                }
                                """)));
    }
}