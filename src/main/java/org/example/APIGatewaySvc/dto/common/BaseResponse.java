package org.example.APIGatewaySvc.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API 응답의 공통 반환 형식을 정의하는 제네릭 클래스
 * 모든 API 엔드포인트에서 일관된 응답 구조를 제공
 * 
 * 주요 기능:
 * - 성공/실패 상태 플래그 제공
 * - 일반적인 메시지 및 데이터 필드
 * - 응답 시간 타임스탬프 자동 기록
 * - 정적 팩토리 메소드를 통한 간편한 응답 생성
 * 
 * @param <T> 응답 데이터의 타입
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    /** API 요청 성공 여부 */
    private boolean success;
    
    /** 응답 메시지 */
    private String message;
    
    /** 응답 데이터 */
    private T data;
    
    /** 응답 생성 시각 */
    private LocalDateTime timestamp;

    /**
     * 성공 응답을 생성하는 정적 팩토리 메소드
     * 주어진 데이터를 포함하여 성공 응답을 생성
     * 
     * @param data 응답에 포함할 데이터
     * @param <T> 데이터 타입
     * @return 성공 응답 객체
     */
    public static <T> BaseResponse<T> success(T data) {
        return BaseResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 커스텀 메시지와 데이터를 포함한 성공 응답을 생성
     * 
     * @param data 응답에 포함할 데이터
     * @param message 커스텀 성공 메시지
     * @param <T> 데이터 타입
     * @return 성공 응답 객체
     */
    public static <T> BaseResponse<T> success(T data, String message) {
        return BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 오류 응답을 생성하는 정적 팩토리 메소드
     * 오류 메시지만 포함하고 데이터는 null로 설정
     * 
     * @param message 오류 메시지
     * @param <T> 데이터 타입
     * @return 오류 응답 객체
     */
    public static <T> BaseResponse<T> error(String message) {
        return BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}