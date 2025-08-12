package org.example.APIGatewaySvc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 에러 상세 정보
 * 
 * StandardResponse의 error 필드에 포함되는 에러 상세 정보
 * 
 * 구조:
 * - type: 에러 분류 (VALIDATION, AUTHENTICATION, AUTHORIZATION 등)
 * - details: 에러 상세 정보 (민감하지 않은 정보만 포함)
 * - fields: 필드별 에러 정보 (validation 에러 시)
 * - traceId: 에러 추적 ID (내부 로깅용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetails {
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("details")
    private Map<String, Object> details;
    
    @JsonProperty("fields")
    private List<FieldError> fields;
    
    @JsonProperty("traceId")
    private String traceId;
    
    public ErrorDetails() {}
    
    public ErrorDetails(String type, Map<String, Object> details, String traceId) {
        this.type = type;
        this.details = details;
        this.traceId = traceId;
    }
    
    public ErrorDetails(String type, Map<String, Object> details, List<FieldError> fields, String traceId) {
        this.type = type;
        this.details = details;
        this.fields = fields;
        this.traceId = traceId;
    }
    
    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
    
    public List<FieldError> getFields() {
        return fields;
    }
    
    public void setFields(List<FieldError> fields) {
        this.fields = fields;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    /**
     * 필드별 에러 정보
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        
        @JsonProperty("field")
        private String field;
        
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("rejectedValue")
        private Object rejectedValue;
        
        public FieldError() {}
        
        public FieldError(String field, String code, String message, Object rejectedValue) {
            this.field = field;
            this.code = code;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
        
        // Getters and Setters
        public String getField() {
            return field;
        }
        
        public void setField(String field) {
            this.field = field;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Object getRejectedValue() {
            return rejectedValue;
        }
        
        public void setRejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
        }
    }
}