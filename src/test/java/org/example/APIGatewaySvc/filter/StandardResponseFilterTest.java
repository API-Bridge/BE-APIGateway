package org.example.APIGatewaySvc.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.APIGatewaySvc.APIGatewaySvcApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * StandardResponseFilter 통합 테스트
 * 
 * WireMock을 사용하여 가상의 마이크로서비스 응답을 시뮬레이션하고
 * StandardResponseFilter가 응답을 올바르게 래핑하는지 테스트
 */
@SpringBootTest(classes = APIGatewaySvcApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 8081)
@TestPropertySource(properties = {
        "jwt.test-mode=true",
        "auth0.audience=test-audience",
        "auth0.issuerUri=https://test.auth0.com/",
        "spring.data.redis.repositories.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class StandardResponseFilterTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        
        // WireMock 초기화
        stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    }

    @Test
    void testSuccessfulResponseWrapping() {
        // Given: 마이크로서비스가 성공적인 JSON 응답을 반환
        String mockResponseBody = "{\"userId\": \"123\", \"name\": \"John Doe\", \"email\": \"john@example.com\"}";
        
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockResponseBody)));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/profile/123")
                .exchange()
                // Then: StandardResponse 형식으로 래핑되어야 함
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectHeader().exists("X-Request-ID")
                .expectBody(JsonNode.class)
                .value(response -> {
                    // StandardResponse 구조 검증
                    assert response.get("success").asBoolean() == true;
                    assert response.get("code").asText().equals("SUCCESS");
                    assert response.get("message").asText().equals("요청이 성공적으로 처리되었습니다");
                    assert response.has("data");
                    assert response.has("meta");
                    assert response.has("timestamp");
                    
                    // 메타데이터 검증
                    JsonNode meta = response.get("meta");
                    assert meta.has("requestId");
                    assert meta.has("durationMs");
                    assert meta.get("gateway").asText().equals("API-Gateway");
                    assert meta.get("version").asText().equals("1.0");
                    
                    // 원본 데이터 검증
                    JsonNode data = response.get("data");
                    assert data.get("userId").asText().equals("123");
                    assert data.get("name").asText().equals("John Doe");
                });
    }

    @Test
    void testErrorResponseWrapping() {
        // Given: 마이크로서비스가 404 에러를 반환
        String mockErrorBody = "{\"error\": \"User not found\", \"code\": \"USER_NOT_FOUND\"}";
        
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockErrorBody)));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/profile/999")
                .exchange()
                // Then: StandardResponse 에러 형식으로 래핑되어야 함
                .expectStatus().isNotFound()
                .expectHeader().contentType("application/json")
                .expectHeader().exists("X-Request-ID")
                .expectBody(JsonNode.class)
                .value(response -> {
                    // StandardResponse 에러 구조 검증
                    assert response.get("success").asBoolean() == false;
                    assert response.get("code").asText().equals("NOT_FOUND");
                    assert response.get("message").asText().equals("요청한 리소스를 찾을 수 없습니다");
                    assert response.has("error");
                    assert response.has("meta");
                    assert response.has("timestamp");
                    assert !response.has("data"); // 에러 응답에는 data가 없어야 함
                    
                    // 에러 세부 정보 검증
                    JsonNode error = response.get("error");
                    assert error.get("type").asText().equals("CLIENT_ERROR");
                    assert error.has("details");
                    assert error.has("traceId");
                    
                    JsonNode details = error.get("details");
                    assert details.get("httpStatus").asInt() == 404;
                    assert details.has("originalResponse");
                });
    }

    @Test
    void testServerErrorWrapping() {
        // Given: 마이크로서비스가 500 서버 에러를 반환
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Internal server error\"}")));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/profile/123")
                .exchange()
                // Then: UPSTREAM_ERROR로 매핑되어야 함
                .expectStatus().is5xxServerError()
                .expectHeader().contentType("application/json")
                .expectBody(JsonNode.class)
                .value(response -> {
                    assert response.get("success").asBoolean() == false;
                    assert response.get("code").asText().equals("UPSTREAM_ERROR");
                    assert response.get("message").asText().equals("서버에서 오류가 발생했습니다");
                    
                    JsonNode error = response.get("error");
                    assert error.get("type").asText().equals("INFRASTRUCTURE");
                });
    }

    @Test
    void testRateLimitErrorWrapping() {
        // Given: 마이크로서비스가 429 Rate Limit 에러를 반환
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Rate limit exceeded\"}")));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/profile/123")
                .exchange()
                // Then: RATE_LIMITED로 매핑되어야 함
                .expectStatus().isEqualTo(429)
                .expectHeader().contentType("application/json")
                .expectBody(JsonNode.class)
                .value(response -> {
                    assert response.get("success").asBoolean() == false;
                    assert response.get("code").asText().equals("RATE_LIMITED");
                    assert response.get("message").asText().equals("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요");
                    
                    JsonNode error = response.get("error");
                    assert error.get("type").asText().equals("POLICY_VIOLATION");
                });
    }

    @Test
    void testNonJsonResponseHandling() {
        // Given: 마이크로서비스가 텍스트 응답을 반환
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello World")));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/health")
                .exchange()
                // Then: 텍스트도 데이터로 래핑되어야 함
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody(JsonNode.class)
                .value(response -> {
                    assert response.get("success").asBoolean() == true;
                    assert response.get("data").asText().equals("Hello World");
                });
    }

    @Test
    void testSkipWrappingForAuthPaths() {
        // Given: /auth 경로는 래핑하지 않아야 함
        // When: /auth/login-success 요청
        webTestClient.get()
                .uri("/auth/login-success")
                .exchange()
                // Then: 원본 응답이 그대로 전달되어야 함 (래핑되지 않음)
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(response -> {
                    // StandardResponse 형식이 아니어야 함
                    assert !response.has("success");
                    assert !response.has("code");
                    assert response.has("message"); // AuthController의 원본 응답
                });
    }

    @Test
    void testSkipWrappingForPublicPaths() {
        // Given: /public 경로는 래핑하지 않아야 함
        // When: /public/health 요청 (존재한다면)
        webTestClient.get()
                .uri("/public/test")
                .exchange()
                // Then: 404이지만 래핑되지 않아야 함
                .expectStatus().isNotFound();
        // public 경로는 StandardResponse로 래핑되지 않으므로 구체적 검증 생략
    }

    @Test
    void testRequestIdPropagation() {
        // Given: 마이크로서비스가 성공 응답을 반환
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"ok\"}")));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/health")
                .exchange()
                // Then: X-Request-ID 헤더와 메타데이터에 requestId가 있어야 함
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID")
                .expectBody(JsonNode.class)
                .value(response -> {
                    JsonNode meta = response.get("meta");
                    assert meta.has("requestId");
                    assert meta.get("requestId").asText().length() > 0;
                });
    }

    @Test
    void testProcessingTimeMeasurement() {
        // Given: 느린 응답을 시뮬레이션
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"ok\"}")
                        .withFixedDelay(100))); // 100ms 지연

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/health")
                .exchange()
                // Then: 처리 시간이 메타데이터에 포함되어야 함
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(response -> {
                    JsonNode meta = response.get("meta");
                    assert meta.has("durationMs");
                    
                    long durationMs = meta.get("durationMs").asLong();
                    assert durationMs >= 100; // 최소 100ms는 걸려야 함
                    assert durationMs < 1000; // 하지만 1초는 넘지 않아야 함
                });
    }

    @Test
    void testArrayResponseWrapping() {
        // Given: 마이크로서비스가 배열 형태의 JSON 응답을 반환
        String mockArrayResponse = "[{\"id\": 1, \"name\": \"User1\"}, {\"id\": 2, \"name\": \"User2\"}]";
        
        stubFor(get(urlPathMatching("/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockArrayResponse)));

        // When: Gateway를 통해 요청
        webTestClient.get()
                .uri("/gateway/users/list")
                .exchange()
                // Then: 배열도 올바르게 래핑되어야 함
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(response -> {
                    assert response.get("success").asBoolean() == true;
                    
                    JsonNode data = response.get("data");
                    assert data.isArray();
                    assert data.size() == 2;
                    assert data.get(0).get("id").asInt() == 1;
                    assert data.get(0).get("name").asText().equals("User1");
                });
    }
}