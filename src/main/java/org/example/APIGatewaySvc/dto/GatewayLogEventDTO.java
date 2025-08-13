package org.example.APIGatewaySvc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Gateway 로그 이벤트 모델
 * Kafka 토픽(logs.gateway)으로 전송되는 로그 메시지 구조
 */
@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayLogEventDTO {

    // Getters and Setters
    @JsonProperty("requestId")
    private String requestId;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("eventType")
    private String eventType; // gateway.request.start, gateway.request.end, gateway.request.error
    
    @JsonProperty("userId")
    private String userId; // JWT sub claim 또는 "anonymous"
    
    @JsonProperty("ip")
    private String ip;
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("routeId")
    private String routeId;
    
    @JsonProperty("status")
    private Integer status; // HTTP 상태 코드 (종료/에러 시에만)
    
    @JsonProperty("durationMs")
    private Long durationMs; // 요청 처리 시간 (종료 시에만)
    
    @JsonProperty("publicApiName")
    private String publicApiName; // API 이름 (있는 경우)
    
    @JsonProperty("userAgent")
    private String userAgent;
    
    @JsonProperty("referer")
    private String referer;
    
    @JsonProperty("errorMessage")
    private String errorMessage; // 에러 발생 시
    
    @JsonProperty("errorType")
    private String errorType; // 에러 타입 (에러 발생 시)
    
    @JsonProperty("headers")
    private Map<String, String> headers; // 주요 헤더들 (마스킹 처리됨)
    
    @JsonProperty("requestSize")
    private Long requestSize; // 요청 크기 (바이트)
    
    @JsonProperty("responseSize")
    private Long responseSize; // 응답 크기 (바이트)
    
    // 기본 생성자
    public GatewayLogEventDTO() {
        this.timestamp = Instant.now();
    }
    
    // 주요 필드 생성자
    public GatewayLogEventDTO(String eventType, String requestId) {
        this();
        this.eventType = eventType;
        this.requestId = requestId;
    }
    
    // Builder 패턴을 위한 정적 메서드
    public static Builder builder() {
        return new Builder();
    }

    // Builder 클래스
    public static class Builder {
        private GatewayLogEventDTO event = new GatewayLogEventDTO();
        
        public Builder requestId(String requestId) {
            event.setRequestId(requestId);
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            event.setTimestamp(timestamp);
            return this;
        }
        
        public Builder eventType(EventType eventType) {
            event.setEventType(eventType.getValue());
            return this;
        }
        
        public Builder eventType(String eventType) {
            event.setEventType(eventType);
            return this;
        }
        
        public Builder userId(String userId) {
            event.setUserId(userId);
            return this;
        }
        
        public Builder ip(String ip) {
            event.setIp(ip);
            return this;
        }
        
        public Builder method(String method) {
            event.setMethod(method);
            return this;
        }
        
        public Builder path(String path) {
            event.setPath(path);
            return this;
        }
        
        public Builder routeId(String routeId) {
            event.setRouteId(routeId);
            return this;
        }
        
        public Builder status(Integer status) {
            event.setStatus(status);
            return this;
        }
        
        public Builder durationMs(Long durationMs) {
            event.setDurationMs(durationMs);
            return this;
        }
        
        public Builder publicApiName(String publicApiName) {
            event.setPublicApiName(publicApiName);
            return this;
        }
        
        public Builder userAgent(String userAgent) {
            event.setUserAgent(userAgent);
            return this;
        }
        
        public Builder referer(String referer) {
            event.setReferer(referer);
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            event.setErrorMessage(errorMessage);
            return this;
        }
        
        public Builder errorType(String errorType) {
            event.setErrorType(errorType);
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            event.setHeaders(headers);
            return this;
        }
        
        public Builder requestSize(Long requestSize) {
            event.setRequestSize(requestSize);
            return this;
        }
        
        public Builder responseSize(Long responseSize) {
            event.setResponseSize(responseSize);
            return this;
        }
        
        public GatewayLogEventDTO build() {
            return event;
        }
    }
    
    // 이벤트 타입 열거형
    public enum EventType {
        REQUEST_START("gateway.request.start"),
        REQUEST_END("gateway.request.end"),
        REQUEST_ERROR("gateway.request.error");
        
        private final String value;
        
        EventType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    @Override
    public String toString() {
        return "GatewayLogEvent{" +
                "requestId='" + requestId + '\'' +
                ", timestamp=" + timestamp +
                ", eventType='" + eventType + '\'' +
                ", userId='" + userId + '\'' +
                ", ip='" + ip + '\'' +
                ", method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", routeId='" + routeId + '\'' +
                ", status=" + status +
                ", durationMs=" + durationMs +
                ", publicApiName='" + publicApiName + '\'' +
                '}';
    }
}