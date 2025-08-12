package org.example.APIGatewaySvc.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.APIGatewaySvc.dto.ErrorDetails;
import org.example.APIGatewaySvc.dto.StandardResponse;
import org.example.APIGatewaySvc.util.ErrorCodeMapper;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API Gateway 표준 응답 필터
 * 
 * 다운스트림 서비스로부터 받은 응답을 StandardResponse 형식으로 래핑하여
 * 클라이언트에게 일관된 형식으로 전달
 * 
 * 주요 기능:
 * - 성공 응답을 StandardResponse로 래핑
 * - 에러 응답을 ErrorCodeMapper를 통해 비즈니스 코드로 변환
 * - X-Request-ID 헤더 추가 및 메타데이터 포함
 * - 요청 처리 시간 계산 (durationMs)
 * - 바이너리 응답은 래핑하지 않고 통과
 * 
 * 적용 대상:
 * - /gateway/** 경로의 모든 마이크로서비스 응답
 * - Content-Type이 application/json인 응답만 래핑
 */
@Component
public class StandardResponseFilter extends AbstractGatewayFilterFactory<StandardResponseFilter.Config> {

    private final ObjectMapper objectMapper;

    public StandardResponseFilter() {
        super(Config.class);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 요청 시작 시간 기록
            Instant startTime = Instant.now();
            String requestId = generateRequestId();
            
            // X-Request-ID 헤더를 요청에 추가 (다운스트림으로 전파)
            exchange.getRequest().mutate()
                    .header("X-Request-ID", requestId)
                    .build();

            // 응답 래핑이 필요한지 확인
            if (shouldSkipWrapping(exchange)) {
                return chain.filter(exchange);
            }

            ServerHttpResponse originalResponse = exchange.getResponse();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();

            // 응답 데코레이터로 응답 내용을 가로채서 래핑
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                    if (body instanceof Flux) {
                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                        
                        return super.writeWith(fluxBody.collectList().flatMap(dataBuffers -> {
                            // 바이너리 콘텐츠 타입인 경우 래핑하지 않고 통과
                            if (isBinaryContent(originalResponse)) {
                                return Flux.fromIterable(dataBuffers).collectList()
                                        .map(list -> bufferFactory.join(list));
                            }

                            // 응답 본문을 문자열로 변환
                            DataBuffer joinedBuffer = bufferFactory.join(dataBuffers);
                            byte[] content = new byte[joinedBuffer.readableByteCount()];
                            joinedBuffer.read(content);
                            DataBufferUtils.release(joinedBuffer);
                            
                            String originalBody = new String(content, StandardCharsets.UTF_8);
                            
                            // 응답 처리 시간 계산
                            Duration processingTime = Duration.between(startTime, Instant.now());
                            long durationMs = processingTime.toMillis();
                            
                            // StandardResponse로 래핑
                            StandardResponse<?> wrappedResponse = wrapResponse(
                                    originalBody, 
                                    originalResponse.getStatusCode(), 
                                    requestId, 
                                    durationMs
                            );
                            
                            try {
                                // JSON으로 직렬화
                                String wrappedJson = objectMapper.writeValueAsString(wrappedResponse);
                                
                                // 응답 헤더 설정
                                originalResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                originalResponse.getHeaders().set("X-Request-ID", requestId);
                                originalResponse.getHeaders().setContentLength(wrappedJson.length());
                                
                                // 새로운 응답 본문 생성
                                DataBuffer buffer = bufferFactory.wrap(wrappedJson.getBytes(StandardCharsets.UTF_8));
                                return Mono.just(buffer);
                                
                            } catch (JsonProcessingException e) {
                                // JSON 직렬화 실패 시 에러 응답 생성
                                return createErrorResponse(bufferFactory, requestId, durationMs);
                            }
                        }));
                    }
                    return super.writeWith(body);
                }
            };

            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        };
    }

    /**
     * 응답 래핑을 건너뛸지 판단
     */
    private boolean shouldSkipWrapping(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        
        // 이미 래핑된 응답이거나 특정 경로는 제외
        return path.startsWith("/auth/") ||
               path.startsWith("/public/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/") ||
               path.equals("/favicon.ico");
    }

    /**
     * 바이너리 콘텐츠인지 확인
     */
    private boolean isBinaryContent(ServerHttpResponse response) {
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null) {
            return false;
        }
        
        // 바이너리 MIME 타입 목록
        List<String> binaryTypes = List.of(
                "image/", "video/", "audio/", "application/pdf", 
                "application/zip", "application/octet-stream"
        );
        
        String contentTypeString = contentType.toString();
        return binaryTypes.stream().anyMatch(contentTypeString::startsWith);
    }

    /**
     * 원본 응답을 StandardResponse로 래핑
     */
    private StandardResponse<?> wrapResponse(String originalBody, HttpStatusCode statusCode, String requestId, long durationMs) {
        Map<String, Object> meta = createMetadata(requestId, durationMs);
        
        if (ErrorCodeMapper.isSuccessStatus(statusCode)) {
            // 성공 응답 래핑
            Object data = parseJsonSafely(originalBody);
            return StandardResponse.success("SUCCESS", "요청이 성공적으로 처리되었습니다", data, meta);
            
        } else {
            // 에러 응답 래핑
            String errorCode = ErrorCodeMapper.mapToErrorCode(statusCode);
            String userMessage = ErrorCodeMapper.mapToUserMessage(statusCode);
            String errorType = ErrorCodeMapper.mapToErrorType(errorCode);
            
            // 원본 에러 응답 파싱 시도
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("httpStatus", statusCode.value());
            errorDetails.put("originalResponse", originalBody);
            
            ErrorDetails error = new ErrorDetails(errorType, errorDetails, requestId);
            return StandardResponse.error(errorCode, userMessage, error, meta);
        }
    }

    /**
     * JSON 파싱을 안전하게 시도
     */
    private Object parseJsonSafely(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            if (jsonNode.isObject()) {
                return objectMapper.convertValue(jsonNode, Map.class);
            } else if (jsonNode.isArray()) {
                return objectMapper.convertValue(jsonNode, List.class);
            } else {
                return jsonNode.asText();
            }
        } catch (JsonProcessingException e) {
            // JSON 파싱 실패 시 원본 문자열 반환
            return jsonString;
        }
    }

    /**
     * 메타데이터 생성
     */
    private Map<String, Object> createMetadata(String requestId, long durationMs) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("requestId", requestId);
        meta.put("durationMs", durationMs);
        meta.put("gateway", "API-Gateway");
        meta.put("version", "1.0");
        return meta;
    }

    /**
     * 요청 ID 생성
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * JSON 직렬화 실패 시 에러 응답 생성
     */
    private Mono<DataBuffer> createErrorResponse(DataBufferFactory bufferFactory, String requestId, long durationMs) {
        Map<String, Object> meta = createMetadata(requestId, durationMs);
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("reason", "Response serialization failed");
        
        ErrorDetails error = new ErrorDetails("INFRASTRUCTURE", errorDetails, requestId);
        StandardResponse<?> errorResponse = StandardResponse.error(
                "GATEWAY_ERROR", 
                "게이트웨이에서 응답 처리 중 오류가 발생했습니다", 
                error, 
                meta
        );
        
        try {
            String errorJson = objectMapper.writeValueAsString(errorResponse);
            return Mono.just(bufferFactory.wrap(errorJson.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException e) {
            // 최후 수단으로 단순 에러 문자열 반환
            String fallbackError = "{\"success\":false,\"code\":\"GATEWAY_ERROR\",\"message\":\"Internal gateway error\"}";
            return Mono.just(bufferFactory.wrap(fallbackError.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * 필터 설정 클래스
     */
    public static class Config {
        // 필요시 설정 프로퍼티 추가
    }
}