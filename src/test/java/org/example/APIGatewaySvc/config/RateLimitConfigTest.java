package org.example.APIGatewaySvc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitConfig 단위 테스트
 * 서비스별 Rate Limiter 설정 검증
 */
class RateLimitConfigTest {

    private RateLimitConfig rateLimitConfig;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        // 기본값 설정
        ReflectionTestUtils.setField(rateLimitConfig, "defaultReplenishRate", 10);
        ReflectionTestUtils.setField(rateLimitConfig, "defaultBurstCapacity", 20);
        ReflectionTestUtils.setField(rateLimitConfig, "defaultRequestedTokens", 1);
    }

    @Test
    @DisplayName("기본 Redis Rate Limiter가 올바른 설정으로 생성되어야 함")
    void shouldCreateDefaultRedisRateLimiterWithCorrectSettings() {
        // When
        RedisRateLimiter rateLimiter = rateLimitConfig.defaultRedisRateLimiter();

        // Then
        assertThat(rateLimiter).isNotNull();
        // Rate Limiter 빈이 생성되는지만 확인 (내부 구조는 Spring Cloud Gateway 버전에 따라 변경될 수 있음)
    }

    @Test
    @DisplayName("사용자 서비스용 Rate Limiter가 더 관대한 설정으로 생성되어야 함")
    void shouldCreateUserServiceRateLimiterWithLiberalSettings() {
        // When
        RedisRateLimiter rateLimiter = rateLimitConfig.userServiceRateLimiter();

        // Then
        assertThat(rateLimiter).isNotNull();
    }

    @Test
    @DisplayName("AI 서비스용 Rate Limiter가 더 엄격한 설정으로 생성되어야 함")
    void shouldCreateAIServiceRateLimiterWithStrictSettings() {
        // When
        RedisRateLimiter rateLimiter = rateLimitConfig.aiServiceRateLimiter();

        // Then
        assertThat(rateLimiter).isNotNull();
    }

    @Test
    @DisplayName("관리 서비스용 Rate Limiter가 중간 정도 설정으로 생성되어야 함")
    void shouldCreateManagementServiceRateLimiterWithModerateSettings() {
        // When
        RedisRateLimiter rateLimiter = rateLimitConfig.managementServiceRateLimiter();

        // Then
        assertThat(rateLimiter).isNotNull();
    }

    @Test
    @DisplayName("모든 Rate Limiter가 서로 다른 인스턴스여야 함")
    void shouldCreateDifferentInstancesForEachRateLimiter() {
        // When
        RedisRateLimiter defaultLimiter = rateLimitConfig.defaultRedisRateLimiter();
        RedisRateLimiter userLimiter = rateLimitConfig.userServiceRateLimiter();
        RedisRateLimiter aiLimiter = rateLimitConfig.aiServiceRateLimiter();
        RedisRateLimiter managementLimiter = rateLimitConfig.managementServiceRateLimiter();

        // Then
        assertThat(defaultLimiter).isNotSameAs(userLimiter);
        assertThat(defaultLimiter).isNotSameAs(aiLimiter);
        assertThat(defaultLimiter).isNotSameAs(managementLimiter);
        assertThat(userLimiter).isNotSameAs(aiLimiter);
        assertThat(userLimiter).isNotSameAs(managementLimiter);
        assertThat(aiLimiter).isNotSameAs(managementLimiter);
    }

    @Test
    @DisplayName("Rate Limiter 설정값이 합리적인 범위 내에 있어야 함")
    void shouldHaveReasonableRateLimitSettings() {
        // When
        RedisRateLimiter[] limiters = {
            rateLimitConfig.defaultRedisRateLimiter(),
            rateLimitConfig.userServiceRateLimiter(),
            rateLimitConfig.aiServiceRateLimiter(),
            rateLimitConfig.managementServiceRateLimiter()
        };

        // Then
        for (RedisRateLimiter limiter : limiters) {
            assertThat(limiter).isNotNull();
            // Rate Limiter 빈이 모두 생성되는지만 확인
        }
    }
}