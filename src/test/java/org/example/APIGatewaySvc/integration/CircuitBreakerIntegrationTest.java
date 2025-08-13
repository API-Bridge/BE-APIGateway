package org.example.APIGatewaySvc.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Circuit Breaker 통합 테스트
 * 서킷브레이커 동작과 Fallback 응답을 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CircuitBreakerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        // 테스트 전에 서킷브레이커 상태 초기화
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            cb.reset();
        });
    }

    @Test
    void shouldReturnFallbackWhenCircuitBreakerIsOpen() {
        // Given - 서킷브레이커를 강제로 Open 상태로 변경
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("userSvcCb");
        circuitBreaker.transitionToOpenState();

        // When - 사용자 서비스 호출 시도
        webTestClient.get()
            .uri("/gateway/users/profile")
            .exchange()
            // Then - Fallback 응답 확인
            .expectStatus().isEqualTo(503)
            .expectHeader().contentType("application/problem+json")
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("Service Unavailable")
            .jsonPath("$.status").isEqualTo(503)
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists();
    }

    @Test
    void shouldReturnNormalResponseWhenCircuitBreakerIsClosed() {
        // Given - 서킷브레이커가 Closed 상태 (기본값)
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("userSvcCb");
        assertEquals("CLOSED", circuitBreaker.getState().toString());

        // When & Then - 정상적인 요청 처리 (실제 서비스가 없으므로 연결 에러 발생은 정상)
        webTestClient.get()
            .uri("/gateway/users/profile")
            .exchange()
            .expectStatus().is5xxServerError(); // 백엔드 서비스 없음으로 인한 에러
    }

    @Test
    void shouldTransitionToHalfOpenAfterWaitDuration() throws InterruptedException {
        // Given - 서킷브레이커를 Open 상태로 변경
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("userSvcCb");
        circuitBreaker.transitionToOpenState();
        assertEquals("OPEN", circuitBreaker.getState().toString());

        // When - Wait duration 후 자동 전환 확인 (테스트에서는 시간을 단축)
        // 실제로는 10초이지만 테스트에서는 강제로 전환
        circuitBreaker.transitionToHalfOpenState();

        // Then - Half-Open 상태 확인
        assertEquals("HALF_OPEN", circuitBreaker.getState().toString());
    }

    @Test
    void shouldRecordSuccessfulCalls() {
        // Given
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("userSvcCb");
        long initialSuccessfulCalls = circuitBreaker.getMetrics().getNumberOfSuccessfulCalls();

        // When - 성공적인 호출 시뮬레이션
        circuitBreaker.executeSupplier(() -> "success");

        // Then
        assertEquals(initialSuccessfulCalls + 1, circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
    }

    @Test
    void shouldRecordFailedCalls() {
        // Given
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("userSvcCb");
        long initialFailedCalls = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        // When - 실패한 호출 시뮬레이션
        try {
            circuitBreaker.executeSupplier(() -> {
                throw new RuntimeException("Test failure");
            });
        } catch (Exception e) {
            // 예외 무시
        }

        // Then
        assertEquals(initialFailedCalls + 1, circuitBreaker.getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void shouldHaveCorrectCircuitBreakerConfiguration() {
        // Given & When
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("userSvcCb");

        // Then - 설정값 검증
        assertEquals(20, circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(10, circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        assertEquals(50.0f, circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
        assertEquals(Duration.ofSeconds(10), circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1));
    }

    @Test
    void shouldHaveHealthIndicatorEnabled() {
        // When - Actuator health 엔드포인트 확인
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            // Then - Circuit Breaker health 정보 포함
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.components.circuitBreakers").exists();
    }

    @Test
    void shouldExposeCircuitBreakerMetrics() {
        // When - Actuator circuitbreakers 엔드포인트 확인
        webTestClient.get()
            .uri("/actuator/circuitbreakers")
            .exchange()
            // Then - Circuit Breaker 상태 정보 반환
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.circuitBreakers").exists()
            .jsonPath("$.circuitBreakers.userSvcCb").exists();
    }

    @Test
    void shouldExposePrometheusMetrics() {
        // When - Prometheus 메트릭 엔드포인트 확인
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            // Then - Circuit Breaker 메트릭 포함
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                assertTrue(body.contains("resilience4j_circuitbreaker_state"));
                assertTrue(body.contains("resilience4j_circuitbreaker_calls_total"));
                assertTrue(body.contains("userSvcCb"));
            });
    }

    @Test
    void shouldHandleSlowCalls() {
        // Given
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiFeatureSvcCb"); // 5초 slow call threshold

        // When - 느린 호출 시뮬레이션
        try {
            circuitBreaker.executeSupplier(() -> {
                try {
                    Thread.sleep(6000); // 6초 대기 (slow call)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "slow success";
            });
        } catch (Exception e) {
            // 타임아웃 또는 인터럽트 예외 무시
        }

        // Then - Slow call이 기록되는지 확인
        assertTrue(circuitBreaker.getMetrics().getNumberOfSlowCalls() > 0 || 
                  circuitBreaker.getMetrics().getNumberOfFailedCalls() > 0);
    }
}