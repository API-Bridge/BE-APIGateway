package org.example.APIGatewaySvc.event.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 모든 이벤트의 기본 클래스
 * MSA 환경에서 마이크로서비스 간 비동기 통신에 사용되는 이벤트의 공통 구조를 정의
 * 
 * 주요 기능:
 * - 이벤트 기본 메타데이터 관리 (이벤트 ID, 타입, 타임스탬프 등)
 * - 마이크로서비스 추적을 위한 상관 ID 및 소스 서비스 정보 제공
 * - 자동 이벤트 ID 생성 및 타임스탬프 기록
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    /** 이벤트 고유 식별자 */
    private String eventId;
    
    /** 이벤트 타입 (예: USER_CREATED, ORDER_UPDATED 등) */
    private String eventType;
    
    /** 이벤트 발생 시간 */
    private LocalDateTime timestamp;
    
    /** 이벤트를 발생시킨 서비스명 */
    private String sourceService;
    
    /** 마이크로서비스 간 요청 추적을 위한 상관 ID */
    private String correlationId;

    /**
     * 이벤트 타입을 지정하여 이벤트를 생성하는 생성자
     * 이벤트 ID, 타임스탬프, 소스 서비스, 상관 ID를 자동으로 설정
     * 
     * @param eventType 이벤트 타입 리터럴
     */
    protected BaseEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.sourceService = "custom-api-svc";
        this.correlationId = UUID.randomUUID().toString();
    }
}