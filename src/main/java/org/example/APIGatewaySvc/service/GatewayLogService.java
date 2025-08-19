package org.example.APIGatewaySvc.service;

import org.example.APIGatewaySvc.dto.GatewayLogEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Gateway 로깅 서비스
 * Kafka를 통한 비동기 로그 전송 및 민감 정보 마스킹 처리
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "kafka.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class GatewayLogService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayLogService.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${gateway.logging.kafka.topic:logs.gateway}")
    private String logTopic;
    
    @Value("${gateway.logging.enabled:true}")
    private boolean loggingEnabled;
    
    @Value("${gateway.logging.mask-sensitive-data:true}")
    private boolean maskSensitiveData;

    public GatewayLogService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 로그 이벤트를 Kafka로 비동기 전송
     * @param logEvent 로그 이벤트
     */
    public void sendLogEvent(GatewayLogEventDTO logEvent) {
        if (!loggingEnabled) {
            return;
        }
        
        try {
            // 민감 정보 마스킹 처리
            if (maskSensitiveData) {
                maskSensitiveInformation(logEvent);
            }
            
            // Kafka로 비동기 전송
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(logTopic, logEvent.getRequestId(), logEvent);
                
            // 전송 결과 처리 (비동기)
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    handleKafkaFailure(logEvent, exception);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Log event sent successfully: requestId={}, eventType={}", 
                            logEvent.getRequestId(), logEvent.getEventType());
                    }
                }
            });
            
        } catch (Exception e) {
            handleKafkaFailure(logEvent, e);
        }
    }

    /**
     * 요청 시작 로그 전송
     * @param requestId 요청 ID
     * @param method HTTP 메서드
     * @param path 요청 경로
     * @param ip 클라이언트 IP
     * @param userId 사용자 ID (없으면 "anonymous")
     * @param userAgent User-Agent 헤더
     * @param referer Referer 헤더
     * @param routeId 라우트 ID
     * @param publicApiName 공개 API 이름
     * @param headers 요청 헤더들
     */
    public void logRequestStart(String requestId, String method, String path, String ip, 
                               String userId, String userAgent, String referer, String routeId,
                               String publicApiName, Map<String, String> headers) {
        
        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId(requestId)
            .eventType(GatewayLogEventDTO.EventType.REQUEST_START)
            .method(method)
            .path(path)
            .ip(ip)
            .userId(userId != null ? userId : "anonymous")
            .userAgent(userAgent)
            .referer(referer)
            .routeId(routeId)
            .publicApiName(publicApiName)
            .headers(headers)
            .build();
            
        sendLogEvent(logEvent);
    }

    /**
     * 요청 완료 로그 전송
     * @param requestId 요청 ID
     * @param status HTTP 상태 코드
     * @param durationMs 처리 시간 (밀리초)
     * @param responseSize 응답 크기
     */
    public void logRequestEnd(String requestId, Integer status, Long durationMs, Long responseSize) {
        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId(requestId)
            .eventType(GatewayLogEventDTO.EventType.REQUEST_END)
            .status(status)
            .durationMs(durationMs)
            .responseSize(responseSize)
            .build();
            
        sendLogEvent(logEvent);
    }

    /**
     * 요청 에러 로그 전송
     * @param requestId 요청 ID
     * @param status HTTP 상태 코드
     * @param durationMs 처리 시간 (밀리초)
     * @param errorMessage 에러 메시지
     * @param errorType 에러 타입
     */
    public void logRequestError(String requestId, Integer status, Long durationMs, 
                               String errorMessage, String errorType) {
        GatewayLogEventDTO logEvent = GatewayLogEventDTO.builder()
            .requestId(requestId)
            .eventType(GatewayLogEventDTO.EventType.REQUEST_ERROR)
            .status(status)
            .durationMs(durationMs)
            .errorMessage(errorMessage)
            .errorType(errorType)
            .build();
            
        sendLogEvent(logEvent);
    }

    /**
     * 로그 이벤트의 민감 정보 마스킹 처리
     * @param logEvent 로그 이벤트
     */
    private void maskSensitiveInformation(GatewayLogEventDTO logEvent) {
        // 헤더에서 민감 정보 마스킹
        if (logEvent.getHeaders() != null) {
            Map<String, String> maskedHeaders = new HashMap<>();
            
            logEvent.getHeaders().forEach((key, value) -> {
                String maskedValue = value;
                
                // Authorization 헤더 마스킹
                if ("authorization".equalsIgnoreCase(key)) {
                    maskedValue = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskAuthorizationHeader(value);
                }
                // API 키 관련 헤더 마스킹
                else if (key.toLowerCase().contains("api") && key.toLowerCase().contains("key")) {
                    maskedValue = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskPartial(value, 4, 3);
                }
                // X-Api-Key 헤더 마스킹
                else if ("x-api-key".equalsIgnoreCase(key)) {
                    maskedValue = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskPartial(value, 4, 3);
                }
                // 기타 민감 정보가 포함될 수 있는 헤더
                else if (key.toLowerCase().contains("token") || 
                         key.toLowerCase().contains("secret") ||
                         key.toLowerCase().contains("password")) {
                    maskedValue = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskPartial(value, 2, 2);
                }
                
                maskedHeaders.put(key, maskedValue);
            });
            
            logEvent.setHeaders(maskedHeaders);
        }

        // URL에서 민감 정보 마스킹 (쿼리 파라미터 등)
        if (logEvent.getPath() != null) {
            String maskedPath = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskSensitiveInfo(logEvent.getPath());
            logEvent.setPath(maskedPath);
        }

        // User-Agent에서 민감 정보 마스킹
        if (logEvent.getUserAgent() != null) {
            String maskedUserAgent = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskSensitiveInfo(logEvent.getUserAgent());
            logEvent.setUserAgent(maskedUserAgent);
        }

        // Referer에서 민감 정보 마스킹
        if (logEvent.getReferer() != null) {
            String maskedReferer = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskSensitiveInfo(logEvent.getReferer());
            logEvent.setReferer(maskedReferer);
        }

        // 에러 메시지에서 민감 정보 마스킹
        if (logEvent.getErrorMessage() != null) {
            String maskedErrorMessage = org.example.APIGatewaySvc.util.SecurityMaskingUtil.maskSensitiveInfo(logEvent.getErrorMessage());
            logEvent.setErrorMessage(maskedErrorMessage);
        }
    }

    /**
     * Kafka 전송 실패 처리
     * @param logEvent 전송 실패한 로그 이벤트
     * @param exception 발생한 예외
     */
    private void handleKafkaFailure(GatewayLogEventDTO logEvent, Throwable exception) {
        // 로컬 로그에 WARN 레벨로 기록하고 무시 (요구사항에 따라)
        logger.warn("Failed to send log event to Kafka: requestId={}, eventType={}, error={}", 
            logEvent.getRequestId(), logEvent.getEventType(), exception.getMessage());
        
        // 디버그 모드에서는 상세 스택 트레이스 출력
        if (logger.isDebugEnabled()) {
            logger.debug("Kafka send failure details", exception);
        }
        
        // 필요시 fallback 로깅 (예: 로컬 파일, 다른 로깅 시스템 등)
        // fallbackLogging(logEvent);
    }

}