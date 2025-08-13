package org.example.APIGatewaySvc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * API Gateway 표준 응답 형식 정의
 * 
 * 모든 응답을 일관된 형식으로 래핑하여 클라이언트에게 전달
 * 
 * 응답 구조:
 * - success: 성공/실패 여부
 * - code: 비즈니스 로직 상태 코드
 * - message: 사용자 친화적 메시지
 * - data: 성공 시 실제 데이터 (null 가능)
 * - error: 실패 시 에러 정보 (null 가능)
 * - meta: 메타데이터 (요청 ID, 실행 시간 등)
 * - timestamp: 응답 생성 시간
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardResponseDTO<T> {

    // Getters
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("data")
    private T data;
    
    @JsonProperty("error")
    private ErrorDetailsDTO error;
    
    @JsonProperty("meta")
    private Map<String, Object> meta;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    // Private constructor to enforce factory methods
    private StandardResponseDTO() {
        this.timestamp = Instant.now();
    }
    
    // Constructor for Builder pattern
    @Builder
    public StandardResponseDTO(boolean success, String code, String message, T data, 
                              ErrorDetailsDTO error, Map<String, Object> meta, Instant timestamp) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.error = error;
        this.meta = meta;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
    
    // Factory method for successful responses
    public static <T> StandardResponseDTO<T> success(String code, String message, T data, Map<String, Object> meta) {
        StandardResponseDTO<T> response = new StandardResponseDTO<>();
        response.success = true;
        response.code = code;
        response.message = message;
        response.data = data;
        response.meta = meta;
        return response;
    }
    
    // Factory method for error responses
    public static <T> StandardResponseDTO<T> error(String code, String message, ErrorDetailsDTO errorDetailsDTO, Map<String, Object> meta) {
        StandardResponseDTO<T> response = new StandardResponseDTO<>();
        response.success = false;
        response.code = code;
        response.message = message;
        response.error = errorDetailsDTO;
        response.meta = meta;
        return response;
    }

}