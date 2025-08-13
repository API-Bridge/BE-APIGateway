package org.example.APIGatewaySvc.service;

import org.example.APIGatewaySvc.dto.GatewayLogEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayLogServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Object>> future;

    private GatewayLogService logService;

    @BeforeEach
    void setUp() {
        logService = new GatewayLogService(kafkaTemplate);
        ReflectionTestUtils.setField(logService, "logTopic", "logs.gateway");
        ReflectionTestUtils.setField(logService, "loggingEnabled", true);
        ReflectionTestUtils.setField(logService, "maskSensitiveData", true);
    }

    @Test
    void shouldSendLogEventToKafka() {
        // Given
        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId("test-request-123")
            .eventType(GatewayLogEventDTO.EventType.REQUEST_START)
            .method("GET")
            .path("/test/path")
            .ip("127.0.0.1")
            .userId("testuser")
            .build();

        when(kafkaTemplate.send(eq("logs.gateway"), eq("test-request-123"), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.sendLogEvent(logEvent);

        // Then
        verify(kafkaTemplate).send(eq("logs.gateway"), eq("test-request-123"), any(GatewayLogEventDTO.class));
    }

    @Test
    void shouldMaskSensitiveInformation() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        headers.put("x-api-key", "secret-api-key-12345");
        headers.put("user-agent", "Mozilla/5.0");

        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId("test-request-123")
            .eventType(GatewayLogEventDTO.EventType.REQUEST_START)
            .headers(headers)
            .path("/test?token=secret123")
            .build();

        when(kafkaTemplate.send(anyString(), anyString(), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.sendLogEvent(logEvent);

        // Then
        ArgumentCaptor<GatewayLogEventDTO> eventCaptor = ArgumentCaptor.forClass(GatewayLogEventDTO.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        GatewayLogEventDTO capturedEvent = eventCaptor.getValue();
        
        // Authorization 헤더가 마스킹되었는지 확인
        String maskedAuth = capturedEvent.getHeaders().get("authorization");
        assertNotNull(maskedAuth);
        assertNotEquals("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c", maskedAuth);
        assertTrue(maskedAuth.contains("*"));

        // API 키가 마스킹되었는지 확인
        String maskedApiKey = capturedEvent.getHeaders().get("x-api-key");
        assertNotNull(maskedApiKey);
        assertNotEquals("secret-api-key-12345", maskedApiKey);
        assertTrue(maskedApiKey.contains("*"));

        // User-Agent는 마스킹되지 않아야 함
        assertEquals("Mozilla/5.0", capturedEvent.getHeaders().get("user-agent"));
    }

    @Test
    void shouldLogRequestStart() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Test Agent");
        headers.put("authorization", "Bearer token123");

        when(kafkaTemplate.send(anyString(), anyString(), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.logRequestStart(
            "req-123",
            "POST",
            "/api/test",
            "192.168.1.100",
            "user123",
            "Test Agent",
            "http://example.com",
            "route-1",
            "Test API",
            headers
        );

        // Then
        ArgumentCaptor<GatewayLogEventDTO> eventCaptor = ArgumentCaptor.forClass(GatewayLogEventDTO.class);
        verify(kafkaTemplate).send(eq("logs.gateway"), eq("req-123"), eventCaptor.capture());

        GatewayLogEventDTO event = eventCaptor.getValue();
        assertEquals("req-123", event.getRequestId());
        assertEquals("gateway.request.start", event.getEventType());
        assertEquals("POST", event.getMethod());
        assertEquals("/api/test", event.getPath());
        assertEquals("192.168.1.100", event.getIp());
        assertEquals("user123", event.getUserId());
        assertEquals("Test Agent", event.getUserAgent());
        assertEquals("http://example.com", event.getReferer());
        assertEquals("route-1", event.getRouteId());
        assertEquals("Test API", event.getPublicApiName());
        assertNotNull(event.getHeaders());
    }

    @Test
    void shouldLogRequestEnd() {
        // Given
        when(kafkaTemplate.send(anyString(), anyString(), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.logRequestEnd("req-123", 200, 1500L, 2048L);

        // Then
        ArgumentCaptor<GatewayLogEventDTO> eventCaptor = ArgumentCaptor.forClass(GatewayLogEventDTO.class);
        verify(kafkaTemplate).send(eq("logs.gateway"), eq("req-123"), eventCaptor.capture());

        GatewayLogEventDTO event = eventCaptor.getValue();
        assertEquals("req-123", event.getRequestId());
        assertEquals("gateway.request.end", event.getEventType());
        assertEquals(Integer.valueOf(200), event.getStatus());
        assertEquals(Long.valueOf(1500), event.getDurationMs());
        assertEquals(Long.valueOf(2048), event.getResponseSize());
    }

    @Test
    void shouldLogRequestError() {
        // Given
        when(kafkaTemplate.send(anyString(), anyString(), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.logRequestError("req-123", 500, 2000L, "Connection timeout", "TimeoutException");

        // Then
        ArgumentCaptor<GatewayLogEventDTO> eventCaptor = ArgumentCaptor.forClass(GatewayLogEventDTO.class);
        verify(kafkaTemplate).send(eq("logs.gateway"), eq("req-123"), eventCaptor.capture());

        GatewayLogEventDTO event = eventCaptor.getValue();
        assertEquals("req-123", event.getRequestId());
        assertEquals("gateway.request.error", event.getEventType());
        assertEquals(Integer.valueOf(500), event.getStatus());
        assertEquals(Long.valueOf(2000), event.getDurationMs());
        assertEquals("Connection timeout", event.getErrorMessage());
        assertEquals("TimeoutException", event.getErrorType());
    }

    @Test
    void shouldNotSendLogWhenLoggingDisabled() {
        // Given
        ReflectionTestUtils.setField(logService, "loggingEnabled", false);
        
        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId("test-request-123")
            .eventType(GatewayLogEventDTO.EventType.REQUEST_START)
            .build();

        // When
        logService.sendLogEvent(logEvent);

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldHandleAnonymousUser() {
        // Given
        when(kafkaTemplate.send(anyString(), anyString(), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.logRequestStart(
            "req-123",
            "GET",
            "/public/api",
            "127.0.0.1",
            null, // anonymous user
            "Test Agent",
            null,
            "public-route",
            "Public API",
            new HashMap<>()
        );

        // Then
        ArgumentCaptor<GatewayLogEventDTO> eventCaptor = ArgumentCaptor.forClass(GatewayLogEventDTO.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        GatewayLogEventDTO event = eventCaptor.getValue();
        assertEquals("anonymous", event.getUserId());
    }

    @Test
    void shouldSkipMaskingWhenDisabled() {
        // Given
        ReflectionTestUtils.setField(logService, "maskSensitiveData", false);

        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer secret-token");

        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId("test-request-123")
            .eventType(GatewayLogEventDTO.EventType.REQUEST_START)
            .headers(headers)
            .build();

        when(kafkaTemplate.send(anyString(), anyString(), any(GatewayLogEventDTO.class)))
            .thenReturn(future);

        // When
        logService.sendLogEvent(logEvent);

        // Then
        ArgumentCaptor<GatewayLogEventDTO> eventCaptor = ArgumentCaptor.forClass(GatewayLogEventDTO.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        GatewayLogEventDTO event = eventCaptor.getValue();
        // 마스킹이 비활성화되었으므로 원본 값이 유지되어야 함
        assertEquals("Bearer secret-token", event.getHeaders().get("authorization"));
    }
}