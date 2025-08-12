package org.example.APIGatewaySvc.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalBlockControllerTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private InternalBlockController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalBlockController(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldBlockUserSuccessfully() {
        // Given
        String type = "user";
        String id = "test-user-123";
        String reason = "Multiple failed login attempts";
        Long ttl = 3600L;
        
        when(valueOperations.set(eq("blocked:user:test-user-123"), eq(reason), eq(Duration.ofSeconds(ttl))))
            .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(controller.block(type, id, ttl, reason))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertTrue((Boolean) body.get("success"));
                assertEquals("user", body.get("type"));
                assertEquals("test-user-123", body.get("id"));
                assertEquals(reason, body.get("reason"));
                assertNotNull(body.get("expiresAt"));
            })
            .verifyComplete();
    }

    @Test
    void shouldBlockIPPermanently() {
        // Given
        String type = "ip";
        String id = "192.168.1.100";
        String reason = "Suspicious activity";
        
        when(valueOperations.set(eq("blocked:ip:192.168.1.100"), eq(reason)))
            .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(controller.block(type, id, null, reason))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertTrue((Boolean) body.get("success"));
                assertEquals("ip", body.get("type"));
                assertEquals("192.168.1.100", body.get("id"));
                assertEquals(reason, body.get("reason"));
                assertNull(body.get("expiresAt"));
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectInvalidBlockType() {
        // Given
        String invalidType = "invalid";
        String id = "test-id";

        // When & Then
        StepVerifier.create(controller.block(invalidType, id, null, null))
            .assertNext(response -> {
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertFalse((Boolean) body.get("success"));
            })
            .verifyComplete();
    }

    @Test
    void shouldUnblockSuccessfully() {
        // Given
        String type = "user";
        String id = "test-user-123";
        
        when(redisTemplate.delete("blocked:user:test-user-123"))
            .thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(controller.unblock(type, id))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertTrue((Boolean) body.get("success"));
                assertEquals("user", body.get("type"));
                assertEquals("test-user-123", body.get("id"));
            })
            .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundWhenUnblockingNonExistentBlock() {
        // Given
        String type = "user";
        String id = "non-existent-user";
        
        when(redisTemplate.delete("blocked:user:non-existent-user"))
            .thenReturn(Mono.just(0L));

        // When & Then
        StepVerifier.create(controller.unblock(type, id))
            .assertNext(response -> {
                assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertFalse((Boolean) body.get("success"));
            })
            .verifyComplete();
    }

    @Test
    void shouldListBlockedUsers() {
        // Given
        String type = "user";
        
        when(redisTemplate.keys("blocked:user:*"))
            .thenReturn(Flux.just("blocked:user:user1", "blocked:user:user2"));
        
        when(valueOperations.get("blocked:user:user1"))
            .thenReturn(Mono.just("Failed login attempts"));
        when(redisTemplate.getExpire("blocked:user:user1"))
            .thenReturn(Mono.just(Duration.ofSeconds(3600)));
            
        when(valueOperations.get("blocked:user:user2"))
            .thenReturn(Mono.just("Suspicious behavior"));
        when(redisTemplate.getExpire("blocked:user:user2"))
            .thenReturn(Mono.just(Duration.ofSeconds(-1)));

        // When & Then
        StepVerifier.create(controller.listBlocked(type))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertTrue((Boolean) body.get("success"));
                assertEquals("user", body.get("type"));
                assertEquals(2, body.get("count"));
            })
            .verifyComplete();
    }

    @Test
    void shouldCheckIfUserIsBlocked() {
        // Given
        String type = "user";
        String id = "test-user";
        
        when(redisTemplate.hasKey("blocked:user:test-user"))
            .thenReturn(Mono.just(true));
        when(valueOperations.get("blocked:user:test-user"))
            .thenReturn(Mono.just("Test reason"));
        when(redisTemplate.getExpire("blocked:user:test-user"))
            .thenReturn(Mono.just(Duration.ofSeconds(1800)));

        // When & Then
        StepVerifier.create(controller.checkBlocked(type, id))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertTrue((Boolean) body.get("success"));
                assertTrue((Boolean) body.get("blocked"));
                assertEquals("user", body.get("type"));
                assertEquals("test-user", body.get("id"));
                assertEquals("Test reason", body.get("reason"));
                assertNotNull(body.get("expiresAt"));
            })
            .verifyComplete();
    }

    @Test
    void shouldCheckIfUserIsNotBlocked() {
        // Given
        String type = "user";
        String id = "free-user";
        
        when(redisTemplate.hasKey("blocked:user:free-user"))
            .thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(controller.checkBlocked(type, id))
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Map<String, Object> body = response.getBody();
                assertNotNull(body);
                assertTrue((Boolean) body.get("success"));
                assertFalse((Boolean) body.get("blocked"));
                assertEquals("user", body.get("type"));
                assertEquals("free-user", body.get("id"));
            })
            .verifyComplete();
    }
}