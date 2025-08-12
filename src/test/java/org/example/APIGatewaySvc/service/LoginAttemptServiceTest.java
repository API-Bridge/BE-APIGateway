package org.example.APIGatewaySvc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    
    @Mock
    private BlockService blockService;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisTemplate, blockService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldRecordFirstLoginFailure() {
        // Given
        String userId = "test-user";
        String ipAddress = "127.0.0.1";
        
        when(valueOperations.increment("login_attempts:" + userId)).thenReturn(Mono.just(1L));
        when(redisTemplate.expire("login_attempts:" + userId, Duration.ofMinutes(15))).thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(loginAttemptService.recordLoginFailure(userId, ipAddress))
            .expectNext(false) // 첫 번째 실패는 차단하지 않음
            .verifyComplete();

        verify(valueOperations).increment("login_attempts:" + userId);
        verify(redisTemplate).expire("login_attempts:" + userId, Duration.ofMinutes(15));
        verifyNoInteractions(blockService);
    }

    @Test
    void shouldBlockUserAfterMaxAttempts() {
        // Given
        String userId = "test-user";
        String ipAddress = "127.0.0.1";
        
        when(valueOperations.increment("login_attempts:" + userId)).thenReturn(Mono.just(5L));
        when(blockService.blockUser(eq(userId), eq(Duration.ofMinutes(30)), anyString())).thenReturn(Mono.empty());
        when(redisTemplate.delete("login_attempts:" + userId)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(loginAttemptService.recordLoginFailure(userId, ipAddress))
            .expectNext(true) // 5번째 실패로 차단됨
            .verifyComplete();

        verify(blockService).blockUser(eq(userId), eq(Duration.ofMinutes(30)), contains("로그인 5회 실패"));
        verify(redisTemplate).delete("login_attempts:" + userId);
    }

    @Test
    void shouldRecordLoginSuccess() {
        // Given
        String userId = "test-user";
        
        when(redisTemplate.delete("login_attempts:" + userId)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(loginAttemptService.recordLoginSuccess(userId))
            .verifyComplete();

        verify(redisTemplate).delete("login_attempts:" + userId);
    }

    @Test
    void shouldGetAttemptCount() {
        // Given
        String userId = "test-user";
        
        when(valueOperations.get("login_attempts:" + userId)).thenReturn(Mono.just("3"));

        // When & Then
        StepVerifier.create(loginAttemptService.getAttemptCount(userId))
            .expectNext(3)
            .verifyComplete();
    }

    @Test
    void shouldReturnZeroWhenNoAttempts() {
        // Given
        String userId = "test-user";
        
        when(valueOperations.get("login_attempts:" + userId)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(loginAttemptService.getAttemptCount(userId))
            .expectNext(0)
            .verifyComplete();
    }

    @Test
    void shouldGetRemainingAttempts() {
        // Given
        String userId = "test-user";
        
        when(valueOperations.get("login_attempts:" + userId)).thenReturn(Mono.just("2"));

        // When & Then
        StepVerifier.create(loginAttemptService.getRemainingAttempts(userId))
            .expectNext(3) // 5 - 2 = 3
            .verifyComplete();
    }

    @Test
    void shouldRecordIpLoginFailure() {
        // Given
        String ipAddress = "192.168.1.100";
        
        when(valueOperations.increment("login_attempts:ip:" + ipAddress)).thenReturn(Mono.just(3L));

        // When & Then
        StepVerifier.create(loginAttemptService.recordIpLoginFailure(ipAddress))
            .expectNext(false) // 3번째 시도는 차단하지 않음
            .verifyComplete();

        verify(valueOperations).increment("login_attempts:ip:" + ipAddress);
    }

    @Test
    void shouldBlockIpAfterMaxAttempts() {
        // Given
        String ipAddress = "192.168.1.100";
        
        when(valueOperations.increment("login_attempts:ip:" + ipAddress)).thenReturn(Mono.just(10L));
        when(blockService.blockIp(eq(ipAddress), eq(Duration.ofMinutes(30)), anyString())).thenReturn(Mono.empty());
        when(redisTemplate.delete("login_attempts:ip:" + ipAddress)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(loginAttemptService.recordIpLoginFailure(ipAddress))
            .expectNext(true) // 10번째 실패로 IP 차단됨
            .verifyComplete();

        verify(blockService).blockIp(eq(ipAddress), eq(Duration.ofMinutes(30)), contains("IP에서 로그인 10회 실패"));
    }

    @Test
    void shouldGetLoginAttemptStats() {
        // Given
        String userId = "test-user";
        
        when(valueOperations.get("login_attempts:" + userId)).thenReturn(Mono.just("2"));
        when(redisTemplate.getExpire("login_attempts:" + userId)).thenReturn(Mono.just(Duration.ofMinutes(10)));

        // When & Then
        StepVerifier.create(loginAttemptService.getLoginAttemptStats(userId))
            .assertNext(stats -> {
                assert stats.getUserId().equals(userId);
                assert stats.getCurrentAttempts() == 2;
                assert stats.getRemainingAttempts() == 3;
                assert stats.getWindowExpiry() != null;
                assert !stats.isBlocked();
            })
            .verifyComplete();
    }

    @Test
    void shouldGetIpAttemptCount() {
        // Given
        String ipAddress = "192.168.1.100";
        
        when(valueOperations.get("login_attempts:ip:" + ipAddress)).thenReturn(Mono.just("5"));

        // When & Then
        StepVerifier.create(loginAttemptService.getIpAttemptCount(ipAddress))
            .expectNext(5)
            .verifyComplete();
    }
}