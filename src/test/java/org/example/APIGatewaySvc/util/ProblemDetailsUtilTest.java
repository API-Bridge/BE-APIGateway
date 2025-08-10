package org.example.APIGatewaySvc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProblemDetailsUtil 단위 테스트
 * RFC 7807 Problem Details 표준 준수 및 민감정보 마스킹 검증
 */
class ProblemDetailsUtilTest {

    private MockServerHttpResponse response;
    private ObjectMapper objectMapper;
    private static final String TEST_REQUEST_ID = "test-request-123";

    @BeforeEach
    void setUp() {
        response = new MockServerHttpResponse();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("401 Unauthorized Problem Details 응답이 올바르게 생성되어야 함")
    void shouldCreateUnauthorizedProblemDetailsResponse() throws Exception {
        // Given
        String detail = "JWT token is invalid";

        // When
        Mono<Void> result = ProblemDetailsUtil.writeUnauthorizedResponse(response, TEST_REQUEST_ID, detail);

        // Then
        StepVerifier.create(result).verifyComplete();

        // HTTP 상태 및 헤더 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo(TEST_REQUEST_ID);

        // JSON 응답 내용 검증
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        assertThat(problemDetails.get("type").asText()).isEqualTo("about:blank");
        assertThat(problemDetails.get("title").asText()).isEqualTo("Authentication failed");
        assertThat(problemDetails.get("status").asInt()).isEqualTo(401);
        assertThat(problemDetails.get("detail").asText()).isEqualTo(detail);
        assertThat(problemDetails.get("instance").asText()).isEqualTo(TEST_REQUEST_ID);
        assertThat(problemDetails.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("403 Forbidden Problem Details 응답이 올바르게 생성되어야 함")
    void shouldCreateForbiddenProblemDetailsResponse() throws Exception {
        // Given
        String detail = "Insufficient permissions";

        // When
        Mono<Void> result = ProblemDetailsUtil.writeForbiddenResponse(response, TEST_REQUEST_ID, detail);

        // Then
        StepVerifier.create(result).verifyComplete();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        assertThat(problemDetails.get("title").asText()).isEqualTo("Access denied");
        assertThat(problemDetails.get("status").asInt()).isEqualTo(403);
        assertThat(problemDetails.get("detail").asText()).isEqualTo(detail);
    }

    @Test
    @DisplayName("503 Service Unavailable Problem Details 응답이 올바르게 생성되어야 함")
    void shouldCreateServiceUnavailableProblemDetailsResponse() throws Exception {
        // Given
        String detail = "External service is down";

        // When
        Mono<Void> result = ProblemDetailsUtil.writeServiceUnavailableResponse(response, TEST_REQUEST_ID, detail);

        // Then
        StepVerifier.create(result).verifyComplete();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        assertThat(problemDetails.get("title").asText()).isEqualTo("Service temporarily unavailable");
        assertThat(problemDetails.get("status").asInt()).isEqualTo(503);
        assertThat(problemDetails.get("detail").asText()).isEqualTo(detail);
    }

    @Test
    @DisplayName("JWT 토큰이 포함된 에러 메시지에서 민감정보가 마스킹되어야 함")
    void shouldSanitizeJWTTokenInErrorMessage() throws Exception {
        // Given
        String messageWithToken = "Invalid JWT token: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c and some other info";

        // When
        Mono<Void> result = ProblemDetailsUtil.writeUnauthorizedResponse(response, TEST_REQUEST_ID, messageWithToken);

        // Then
        StepVerifier.create(result).verifyComplete();
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        String sanitizedDetail = problemDetails.get("detail").asText();
        
        // JWT 토큰이 마스킹되었는지 확인
        assertThat(sanitizedDetail).contains("Bearer [REDACTED]");
        assertThat(sanitizedDetail).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(sanitizedDetail).contains("and some other info"); // 나머지 내용은 유지
    }

    @Test
    @DisplayName("이메일 주소가 포함된 에러 메시지에서 민감정보가 마스킹되어야 함")
    void shouldSanitizeEmailInErrorMessage() throws Exception {
        // Given
        String messageWithEmail = "User not found: test@example.com does not exist in the system";

        // When
        Mono<Void> result = ProblemDetailsUtil.writeUnauthorizedResponse(response, TEST_REQUEST_ID, messageWithEmail);

        // Then
        StepVerifier.create(result).verifyComplete();
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        String sanitizedDetail = problemDetails.get("detail").asText();
        
        // 이메일이 부분 마스킹되었는지 확인
        assertThat(sanitizedDetail).contains("test@[REDACTED]");
        assertThat(sanitizedDetail).doesNotContain("test@example.com");
        assertThat(sanitizedDetail).contains("does not exist in the system");
    }

    @Test
    @DisplayName("과도하게 긴 에러 메시지는 적절히 잘려야 함")
    void shouldTruncateLongErrorMessage() throws Exception {
        // Given
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longMessage.append("This is a very long error message that exceeds the maximum length limit. ");
        }

        // When
        Mono<Void> result = ProblemDetailsUtil.writeUnauthorizedResponse(response, TEST_REQUEST_ID, longMessage.toString());

        // Then
        StepVerifier.create(result).verifyComplete();
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        String sanitizedDetail = problemDetails.get("detail").asText();
        
        // 메시지 길이가 200자 이하로 제한되고 ... 으로 끝나는지 확인
        assertThat(sanitizedDetail.length()).isLessThanOrEqualTo(200);
        assertThat(sanitizedDetail).endsWith("...");
    }

    @Test
    @DisplayName("null 에러 메시지는 기본 메시지로 대체되어야 함")
    void shouldHandleNullErrorMessage() throws Exception {
        // When
        Mono<Void> result = ProblemDetailsUtil.writeUnauthorizedResponse(response, TEST_REQUEST_ID, null);

        // Then
        StepVerifier.create(result).verifyComplete();
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        String detail = problemDetails.get("detail").asText();
        assertThat(detail).isEqualTo("An error occurred");
    }

    @Test
    @DisplayName("빈 에러 메시지는 기본 메시지로 대체되어야 함")
    void shouldHandleEmptyErrorMessage() throws Exception {
        // When
        Mono<Void> result = ProblemDetailsUtil.writeUnauthorizedResponse(response, TEST_REQUEST_ID, "");

        // Then
        StepVerifier.create(result).verifyComplete();
        
        DataBuffer buffer = response.getBody().blockLast();
        String jsonResponse = StandardCharsets.UTF_8.decode(buffer.asByteBuffer()).toString();
        JsonNode problemDetails = objectMapper.readTree(jsonResponse);

        String detail = problemDetails.get("detail").asText();
        assertThat(detail).isEqualTo("An error occurred");
    }
}