package org.example.APIGatewaySvc.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class LoginAttemptIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
    }

    @Test
    void shouldTrackLoginAttempts() {
        String userId = "test-user-123";

        // 1. 초기 상태 확인 - 시도 기록 없음
        webTestClient.get()
            .uri("/internal/login-attempts/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.currentAttempts").isEqualTo(0)
            .jsonPath("$.remainingAttempts").isEqualTo(5)
            .jsonPath("$.blocked").isEqualTo(false);

        // 2. 로그인 실패 시뮬레이션 (401 응답으로 실패 추적)
        for (int i = 1; i <= 3; i++) {
            // JWT 토큰 없이 인증이 필요한 엔드포인트 호출 (401 발생)
            webTestClient.get()
                .uri("/gateway/users/me")
                .header("Authorization", "Bearer invalid-token-for-user-" + userId)
                .exchange()
                .expectStatus().isUnauthorized();

            // 시도 횟수 확인
            webTestClient.get()
                .uri("/internal/login-attempts/user/{userId}", userId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currentAttempts").isEqualTo(i)
                .jsonPath("$.remainingAttempts").isEqualTo(5 - i)
                .jsonPath("$.blocked").isEqualTo(false);
        }
    }

    @Test
    void shouldBlockUserAfterMaxAttempts() {
        String userId = "block-test-user";

        // 5번 로그인 실패 시뮬레이션
        for (int i = 1; i <= 5; i++) {
            webTestClient.get()
                .uri("/gateway/users/me")
                .header("Authorization", "Bearer invalid-token-for-user-" + userId)
                .exchange()
                .expectStatus().isUnauthorized();
        }

        // 사용자 차단 상태 확인
        webTestClient.get()
            .uri("/internal/block/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.blocked").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("user")
            .jsonPath("$.id").isEqualTo(userId);

        // 시도 횟수 초기화 확인 (차단 후 카운터 리셋)
        webTestClient.get()
            .uri("/internal/login-attempts/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentAttempts").isEqualTo(0);
    }

    @Test
    void shouldResetAttemptCount() {
        String userId = "reset-test-user";

        // 몇 번 실패 시뮬레이션
        for (int i = 1; i <= 3; i++) {
            webTestClient.get()
                .uri("/gateway/users/me")
                .header("Authorization", "Bearer invalid-token-for-user-" + userId)
                .exchange()
                .expectStatus().isUnauthorized();
        }

        // 시도 횟수 확인
        webTestClient.get()
            .uri("/internal/login-attempts/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentAttempts").isEqualTo(3);

        // 시도 횟수 수동 초기화
        webTestClient.delete()
            .uri("/internal/login-attempts/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.userId").isEqualTo(userId);

        // 초기화 확인
        webTestClient.get()
            .uri("/internal/login-attempts/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentAttempts").isEqualTo(0)
            .jsonPath("$.remainingAttempts").isEqualTo(5);
    }

    @Test
    void shouldTrackIpAttempts() {
        String testIp = "192.168.1.100";

        // IP별 시도 횟수 확인 - 초기 상태
        webTestClient.get()
            .uri("/internal/login-attempts/ip/{ipAddress}", testIp)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.currentAttempts").isEqualTo(0)
            .jsonPath("$.maxAttempts").isEqualTo(10);

        // X-Forwarded-For 헤더로 IP 시뮬레이션하여 실패 시도
        for (int i = 1; i <= 3; i++) {
            webTestClient.get()
                .uri("/gateway/users/me")
                .header("Authorization", "Bearer invalid-token")
                .header("X-Forwarded-For", testIp)
                .exchange()
                .expectStatus().isUnauthorized();
        }

        // IP별 시도 횟수 확인
        webTestClient.get()
            .uri("/internal/login-attempts/ip/{ipAddress}", testIp)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentAttempts").isEqualTo(3)
            .jsonPath("$.remainingAttempts").isEqualTo(7);
    }

    @Test
    void shouldIntegrateWithBlockSystem() {
        String userId = "integration-test-user";

        // 1. 수동 차단
        webTestClient.post()
            .uri("/internal/block/user?id={id}&ttlSeconds=60&reason=Manual block for testing", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk();

        // 2. 차단된 사용자는 요청 자체가 403으로 차단됨
        webTestClient.get()
            .uri("/gateway/users/me")
            .header("Authorization", "Bearer valid-token-for-user-" + userId)
            .exchange()
            .expectStatus().isForbidden();

        // 3. 차단 해제
        webTestClient.delete()
            .uri("/internal/block/user/{id}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk();

        // 4. 차단 해제 후 시도 횟수 초기화 확인
        webTestClient.get()
            .uri("/internal/login-attempts/user/{userId}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.currentAttempts").isEqualTo(0);
    }

    @Test
    void shouldHandleHighVolumeRequests() {
        String baseUserId = "load-test-user-";

        // 여러 사용자 동시 실패 시뮬레이션
        for (int userId = 1; userId <= 10; userId++) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                webTestClient.get()
                    .uri("/gateway/users/me")
                    .header("Authorization", "Bearer invalid-token-for-user-" + baseUserId + userId)
                    .exchange()
                    .expectStatus().isUnauthorized();
            }
        }

        // 각 사용자의 시도 횟수 확인
        for (int userId = 1; userId <= 10; userId++) {
            webTestClient.get()
                .uri("/internal/login-attempts/user/{userId}", baseUserId + userId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currentAttempts").isEqualTo(3)
                .jsonPath("$.remainingAttempts").isEqualTo(2);
        }
    }
}