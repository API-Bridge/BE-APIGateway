package org.example.APIGatewaySvc.filter;

import org.example.APIGatewaySvc.service.GatewayLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GatewayLoggingFilterTest {

    @Mock
    private GatewayLogService logService;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private HttpHeaders requestHeaders;

    @Mock
    private HttpHeaders responseHeaders;

    private GatewayLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayLoggingFilter(logService);

        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getHeaders()).thenReturn(requestHeaders);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(exchange.getAttributes()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
    }

    @Test
    void shouldLogRequestStartAndEnd() {
        // Given
        setupBasicRequest();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        verify(logService).logRequestStart(
            anyString(), // requestId
            eq("GET"),
            eq("/test/path"),
            eq("127.0.0.1"),
            eq(null), // anonymous user
            eq("Test Agent"),
            eq("http://example.com"),
            eq("unknown"), // routeId
            eq(null), // publicApiName
            any(Map.class) // headers
        );

        verify(logService).logRequestEnd(
            anyString(), // requestId
            eq(200),
            anyLong(), // durationMs
            eq(null) // responseSize
        );
    }

    @Test
    void shouldLogRequestError() {
        // Given
        setupBasicRequest();
        RuntimeException error = new RuntimeException("Test error");
        when(chain.filter(exchange)).thenReturn(Mono.error(error));
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        // When & Then
        StepVerifier.create(filter.filter(exchange, chain))
            .expectError(RuntimeException.class)
            .verify();

        verify(logService).logRequestStart(anyString(), anyString(), anyString(), anyString(), 
            eq(null), anyString(), anyString(), anyString(), eq(null), any(Map.class));
        
        verify(logService).logRequestError(
            anyString(), // requestId
            eq(500),
            anyLong(), // durationMs
            eq("Test error"),
            eq("RuntimeException")
        );
    }

    @Test
    void shouldExtractClientIpFromHeaders() {
        // Given
        setupBasicRequest();
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).logRequestStart(
            anyString(),
            anyString(),
            anyString(),
            ipCaptor.capture(), // IP should be from X-Forwarded-For
            any(), // userId can be null
            anyString(),
            anyString(),
            anyString(),
            any(), // publicApiName can be null
            any(Map.class)
        );

        assertEquals("192.168.1.100", ipCaptor.getValue());
    }

    @Test
    void shouldExtractRequestIdFromHeader() {
        // Given
        setupBasicRequest();
        when(requestHeaders.getFirst("X-Request-ID")).thenReturn("custom-request-id-123");
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).logRequestStart(
            requestIdCaptor.capture(), // Should use custom request ID
            anyString(),
            anyString(),
            anyString(),
            any(), // userId can be null
            anyString(),
            anyString(),
            anyString(),
            any(), // publicApiName can be null
            any(Map.class)
        );

        assertEquals("custom-request-id-123", requestIdCaptor.getValue());
    }

    @Test
    void shouldDetectPublicApiName() {
        // Given
        setupBasicRequest();
        when(request.getURI()).thenReturn(URI.create("/gateway/users/profile"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        ArgumentCaptor<String> apiNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).logRequestStart(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(), // userId can be null
            anyString(),
            anyString(),
            anyString(),
            apiNameCaptor.capture(), // Should detect Users API
            any(Map.class)
        );

        assertEquals("Users API", apiNameCaptor.getValue());
    }

    @Test
    void shouldCollectImportantHeaders() {
        // Given
        setupBasicRequest();
        when(requestHeaders.getFirst("authorization")).thenReturn("Bearer token123");
        when(requestHeaders.getFirst("x-api-key")).thenReturn("api-key-456");
        when(requestHeaders.getFirst("content-type")).thenReturn("application/json");
        when(requestHeaders.getFirst("accept")).thenReturn("application/json");
        when(requestHeaders.getFirst("origin")).thenReturn("https://example.com");

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(logService).logRequestStart(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(), // userId can be null
            anyString(),
            anyString(),
            anyString(),
            any(), // publicApiName can be null
            headersCaptor.capture()
        );

        Map<String, String> capturedHeaders = headersCaptor.getValue();
        assertEquals("Bearer token123", capturedHeaders.get("authorization"));
        assertEquals("api-key-456", capturedHeaders.get("x-api-key"));
        assertEquals("application/json", capturedHeaders.get("content-type"));
        assertEquals("application/json", capturedHeaders.get("accept"));
        assertEquals("https://example.com", capturedHeaders.get("origin"));
    }

    @Test
    void shouldMeasureDurationAccurately() {
        // Given
        setupBasicRequest();
        when(chain.filter(exchange)).thenReturn(Mono.delay(java.time.Duration.ofMillis(100)).then());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(logService).logRequestEnd(
            anyString(),
            anyInt(),
            durationCaptor.capture(),
            any()
        );

        Long duration = durationCaptor.getValue();
        assertNotNull(duration);
        assertTrue(duration >= 50, "Duration should be at least 50ms"); // 약간의 여유
    }

    @Test
    void shouldHandleResponseSize() {
        // Given
        setupBasicRequest();
        when(responseHeaders.getFirst("Content-Length")).thenReturn("2048");
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        ArgumentCaptor<Long> responseSizeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(logService).logRequestEnd(
            anyString(),
            anyInt(),
            anyLong(),
            responseSizeCaptor.capture()
        );

        assertEquals(Long.valueOf(2048), responseSizeCaptor.getValue());
    }

    @Test
    void shouldHandleNullResponseSize() {
        // Given
        setupBasicRequest();
        when(responseHeaders.getFirst("Content-Length")).thenReturn(null);
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Then
        verify(logService).logRequestEnd(
            anyString(),
            anyInt(),
            anyLong(),
            eq(null) // Should be null when no Content-Length
        );
    }

    @Test
    void shouldHaveCorrectOrder() {
        // When & Then
        assertEquals(-2147483638, filter.getOrder()); // HIGHEST_PRECEDENCE + 10
    }

    private void setupBasicRequest() {
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("/test/path"));
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        
        when(requestHeaders.getFirst("X-Request-ID")).thenReturn(null);
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn(null);
        when(requestHeaders.getFirst("X-Real-IP")).thenReturn(null);
        when(requestHeaders.getFirst("Content-Length")).thenReturn(null);
        
        when(requestHeaders.getFirst("authorization")).thenReturn(null);
        when(requestHeaders.getFirst("x-api-key")).thenReturn(null);
        when(requestHeaders.getFirst("user-agent")).thenReturn("Test Agent");
        when(requestHeaders.getFirst("referer")).thenReturn("http://example.com");
        when(requestHeaders.getFirst("content-type")).thenReturn(null);
        when(requestHeaders.getFirst("accept")).thenReturn(null);
        when(requestHeaders.getFirst("x-forwarded-for")).thenReturn(null);
        when(requestHeaders.getFirst("x-real-ip")).thenReturn(null);
        when(requestHeaders.getFirst("x-request-id")).thenReturn(null);
        when(requestHeaders.getFirst("origin")).thenReturn(null);
        when(requestHeaders.getFirst("host")).thenReturn(null);
        
        when(responseHeaders.getFirst("Content-Length")).thenReturn(null);
    }
}