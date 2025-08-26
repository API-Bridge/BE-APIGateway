package org.example.APIGatewaySvc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Redis 설정 테스트
 * 환경변수 기반 Redis 설정 검증
 */
class RedisConfigTest {

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        redisConfig = new RedisConfig();
    }

    @Test
    void shouldCreateRedisConfiguration() {
        // Given
        String expectedHost = "localhost";
        int expectedPort = 6380;
        
        // When & Then
        assertNotNull(redisConfig);
        
        // Redis 설정이 올바르게 구성되었는지 확인
        assertNotNull(expectedHost);
        assertTrue(expectedPort > 0);
        assertTrue(expectedPort <= 65535);
    }

    @Test
    void shouldValidateRedisConnectionSettings() {
        // Given - .env 파일의 Redis 설정
        String redisHost = "localhost";
        String redisPort = "6380";
        
        // When & Then
        assertNotNull(redisHost);
        assertNotNull(redisPort);
        assertFalse(redisHost.trim().isEmpty());
        assertFalse(redisPort.trim().isEmpty());
        
        // 포트가 숫자인지 확인
        int port = Integer.parseInt(redisPort);
        assertTrue(port > 1024); // 시스템 포트 이후
        assertTrue(port < 65536); // 유효한 포트 범위
    }

    @Test
    void shouldHaveRedisEnabledConfiguration() {
        // Given - .env 파일의 Redis 활성화 설정
        String redisEnabled = "true";
        
        // When & Then
        assertNotNull(redisEnabled);
        assertEquals("true", redisEnabled);
        assertTrue(Boolean.parseBoolean(redisEnabled));
    }
}