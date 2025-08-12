package org.example.APIGatewaySvc.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class BlockFeatureIntegrationTest {

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
    void shouldBlockAndUnblockUser() {
        String userId = "integration-test-user";
        String reason = "Test blocking";

        // 1. 사용자 차단
        webTestClient.post()
            .uri("/internal/block/user?id={id}&reason={reason}&ttlSeconds=60", userId, reason)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("user")
            .jsonPath("$.id").isEqualTo(userId)
            .jsonPath("$.reason").isEqualTo(reason)
            .jsonPath("$.expiresAt").isNotEmpty();

        // 2. 차단 상태 확인
        webTestClient.get()
            .uri("/internal/block/user/{id}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.blocked").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("user")
            .jsonPath("$.id").isEqualTo(userId);

        // 3. 차단 목록 조회
        webTestClient.get()
            .uri("/internal/block/user")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("user")
            .jsonPath("$.count").isEqualTo(1)
            .jsonPath("$.blocked[0].id").isEqualTo(userId);

        // 4. 사용자 차단 해제
        webTestClient.delete()
            .uri("/internal/block/user/{id}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("user")
            .jsonPath("$.id").isEqualTo(userId);

        // 5. 차단 해제 확인
        webTestClient.get()
            .uri("/internal/block/user/{id}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.blocked").isEqualTo(false);
    }

    @Test
    void shouldBlockIP() {
        String ip = "192.168.1.100";
        String reason = "Suspicious activity";

        // IP 차단 (영구)
        webTestClient.post()
            .uri("/internal/block/ip?id={id}&reason={reason}", ip, reason)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("ip")
            .jsonPath("$.id").isEqualTo(ip)
            .jsonPath("$.reason").isEqualTo(reason)
            .jsonPath("$.expiresAt").doesNotExist();

        // 차단 상태 확인
        webTestClient.get()
            .uri("/internal/block/ip/{id}", ip)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.blocked").isEqualTo(true)
            .jsonPath("$.expiresAt").isEmpty();
    }

    @Test
    void shouldBlockAPIKey() {
        String apiKey = "test-api-key-123";
        String reason = "Rate limit exceeded";
        Long ttl = 30L;

        // API 키 차단 (임시)
        webTestClient.post()
            .uri("/internal/block/key?id={id}&reason={reason}&ttlSeconds={ttl}", apiKey, reason, ttl)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.type").isEqualTo("key")
            .jsonPath("$.id").isEqualTo(apiKey)
            .jsonPath("$.reason").isEqualTo(reason)
            .jsonPath("$.expiresAt").isNotEmpty();
    }

    @Test
    void shouldReturnBadRequestForInvalidType() {
        webTestClient.post()
            .uri("/internal/block/invalid?id=test")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void shouldReturnNotFoundWhenUnblockingNonExistentBlock() {
        webTestClient.delete()
            .uri("/internal/block/user/non-existent-user")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void shouldTestTTLExpiration() {
        String userId = "ttl-test-user";
        String reason = "TTL test";

        // 2초 TTL로 차단
        webTestClient.post()
            .uri("/internal/block/user?id={id}&reason={reason}&ttlSeconds=2", userId, reason)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk();

        // 즉시 차단 상태 확인
        webTestClient.get()
            .uri("/internal/block/user/{id}", userId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.blocked").isEqualTo(true);

        // 3초 후 차단 해제 확인
        StepVerifier.create(
            Mono.delay(Duration.ofSeconds(3))
                .then(redisTemplate.hasKey("blocked:user:" + userId))
        )
        .expectNext(false)
        .verifyComplete();
    }
}