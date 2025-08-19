package org.example.APIGatewaySvc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kafka Producer 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SendResult<String, Object> sendResult;

    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        kafkaProducerService = new KafkaProducerService(kafkaTemplate);
    }

    @Test
    void testSendMessage() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        kafkaProducerService.sendMessage("test-topic", "test-key", "test-message");

        // Then
        verify(kafkaTemplate).send("test-topic", "test-key", "test-message");
    }

    @Test
    void testSendGatewayLog() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), isNull(), any())).thenReturn(future);
        
        Object logEvent = "test-log-event";

        // When
        kafkaProducerService.sendGatewayLog(logEvent);

        // Then
        verify(kafkaTemplate).send("logs.gateway", null, logEvent);
    }

    @Test
    void testSendAuthEvent() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), isNull(), any())).thenReturn(future);
        
        Object authEvent = "test-auth-event";

        // When
        kafkaProducerService.sendAuthEvent(authEvent);

        // Then
        verify(kafkaTemplate).send("events.auth", null, authEvent);
    }

    @Test
    void testSendRateLimitEvent() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), isNull(), any())).thenReturn(future);
        
        Object rateLimitEvent = "test-ratelimit-event";

        // When
        kafkaProducerService.sendRateLimitEvent(rateLimitEvent);

        // Then
        verify(kafkaTemplate).send("events.ratelimit", null, rateLimitEvent);
    }

    @Test
    void testSendMessageSyncSuccess() throws Exception {
        // Given
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        boolean result = kafkaProducerService.sendMessageSync("test-topic", "test-key", "test-message");

        // Then
        assertThat(result).isTrue();
        verify(kafkaTemplate).send("test-topic", "test-key", "test-message");
    }

    @Test
    void testSendMessageSyncFailure() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // When
        boolean result = kafkaProducerService.sendMessageSync("test-topic", "test-key", "test-message");

        // Then
        assertThat(result).isFalse();
    }
}