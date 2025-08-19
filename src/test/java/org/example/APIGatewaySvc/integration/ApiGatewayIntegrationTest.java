package org.example.APIGatewaySvc.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * API Gateway 통합 테스트
 * 전체 애플리케이션 컨텍스트를 로드하여 실제 환경과 유사한 조건에서 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "AUTH0_CLIENT_ID=test-client-id",
    "AUTH0_CLIENT_SECRET=test-client-secret", 
    "AUTH0_ISSUER_URI=https://test.auth0.com/",
    "AUTH0_AUDIENCE=test-audience",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class ApiGatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testHealthEndpoint() {
        // Health 엔드포인트는 인증 없이 접근 가능해야 함
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testAuth0ConfigEndpoint() {
        // Auth0 설정 엔드포인트는 인증 없이 접근 가능해야 함
        webTestClient.get()
            .uri("/auth/auth0-config")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.domain").exists()
            .jsonPath("$.clientId").exists()
            .jsonPath("$.audience").exists();
    }

    @Test
    void testSwaggerUiEndpoint() {
        // Swagger UI는 인증 없이 접근 가능해야 함
        webTestClient.get()
            .uri("/swagger-ui.html")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void testApiDocsEndpoint() {
        // API 문서는 인증 없이 접근 가능해야 함
        webTestClient.get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.openapi").exists()
            .jsonPath("$.info.title").exists();
    }

    @Test
    void testProtectedEndpointWithoutToken() {
        // 보호된 엔드포인트는 토큰 없이 접근 시 401 반환
        webTestClient.get()
            .uri("/test/protected")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Unauthorized")
            .jsonPath("$.message").isEqualTo("JWT token is required for this endpoint");
    }

    @Test
    void testKafkaHealthEndpoint() {
        // Kafka 헬스체크 엔드포인트 테스트
        webTestClient.get()
            .uri("/test/kafka/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.kafka_available").exists()
            .jsonPath("$.status").exists()
            .jsonPath("$.timestamp").exists();
    }

    @Test
    void testAuthLoginRedirect() {
        // Auth 로그인 엔드포인트는 Auth0로 리다이렉트해야 함
        webTestClient.get()
            .uri("/auth/login")
            .exchange()
            .expectStatus().isFound(); // 302 Redirect
    }

    @Test
    void testRootEndpoint() {
        // 루트 엔드포인트는 접근 가능해야 함
        webTestClient.get()
            .uri("/")
            .exchange()
            .expectStatus().isNotFound(); // 실제 루트 핸들러가 없으므로 404
    }

    @Test
    void testCorsOptions() {
        // CORS preflight 요청은 허용되어야 함
        webTestClient.options()
            .uri("/test/any-endpoint")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void testEnvironmentVariableInjection() {
        // 환경변수가 올바르게 주입되었는지 Auth0 config를 통해 확인
        webTestClient.get()
            .uri("/auth/auth0-config")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.clientId").isEqualTo("test-client-id")
            .jsonPath("$.audience").isEqualTo("test-audience");
    }
}