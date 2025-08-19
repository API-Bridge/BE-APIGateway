package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.APIGatewaySvc.util.ProblemDetailsUtil;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Circuit Breaker Fallback 처리 컨트롤러
 * 다운스트림 서비스 장애 시 표준화된 에러 응답 제공
 * 
 * 주요 기능:
 * - 서비스별 Circuit Breaker Fallback 응답
 * - RFC 7807 Problem Details 표준 준수
 * - 서비스 식별을 통한 구체적인 에러 메시지 제공
 * - Request ID를 통한 에러 추적성 보장
 */
@RestController
@RequestMapping("/fallback")
@Tag(name = "Fallback Controller", description = "Circuit Breaker Fallback - 서비스 장애 시 대체 응답")
public class FallbackController {

    /**
     * 일반적인 서비스 불가 상태 Fallback
     * Circuit Breaker가 열린 상태에서 호출
     * 
     * @param exchange ServerWebExchange 웹 교환 객체
     * @return Mono<Void> Service Unavailable 응답
     */
    @RequestMapping("/service-unavailable")
    @Operation(
        summary = "Service Unavailable Fallback", 
        description = """
            **일반적인 서비스 불가 상태 Fallback**
            
            **트리거 조건**: Circuit Breaker가 열린 상태
            
            **목적**: 
            - 다운스트림 서비스 장애 시 표준화된 에러 응답
            - RFC 7807 Problem Details 표준 준수
            - 사용자 친화적인 에러 메시지 제공
            
            **응답 형식**:
            - HTTP Status: 503 Service Unavailable
            - Content-Type: application/problem+json
            - Request ID 포함으로 에러 추적 가능
            
            **참고**: 이 엔드포인트는 직접 호출하지 마세요. Circuit Breaker에 의해 자동 호출됩니다.
            """
    )
    @ApiResponses(value = {
@ApiResponse(responseCode = "503", description = "서비스를 일시적으로 사용할 수 없습니다")
    })
    public Mono<Void> serviceUnavailable(@Parameter(hidden = true) ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        String requestId = generateRequestId();
        String detail = "The requested service is temporarily unavailable. Please try again later.";
        
        return ProblemDetailsUtil.writeServiceUnavailableResponse(response, requestId, detail);
    }

    /**
     * 특정 서비스별 Fallback 처리
     * 서비스 이름에 따라 구체적인 에러 메시지 제공
     * 
     * @param serviceName 장애가 발생한 서비스 이름
     * @param exchange ServerWebExchange 웹 교환 객체
     * @return Mono<Void> 서비스별 맞춤 에러 응답
     */
    @RequestMapping("/{serviceName}")
    public Mono<Void> serviceFallback(@PathVariable String serviceName, ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        String requestId = generateRequestId();
        String detail = generateServiceSpecificMessage(serviceName);
        
        return ProblemDetailsUtil.writeServiceUnavailableResponse(response, requestId, detail);
    }

    /**
     * 서비스별 구체적인 에러 메시지 생성
     * 사용자가 이해하기 쉬운 맞춤형 메시지 제공
     * 
     * @param serviceName 서비스 이름
     * @return String 서비스별 에러 메시지
     */
    private String generateServiceSpecificMessage(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "user-service" -> 
                "User management service is temporarily unavailable. Login and profile features may not work.";
            case "api-management", "apimgmt" -> 
                "API management service is temporarily unavailable. API configuration features may not work.";
            case "custom-api-management", "customapi" -> 
                "Custom API service is temporarily unavailable. Custom API features may not work.";
            case "ai-feature", "aifeature" -> 
                "AI feature service is temporarily unavailable. AI-powered features may not work.";
            case "system-management", "sysmgmt" -> 
                "System management service is temporarily unavailable. Admin features may not work.";
            default -> 
                String.format("The %s service is temporarily unavailable. Please try again later.", serviceName);
        };
    }

    /**
     * Request ID 생성 (Fallback용)
     * 정상적인 RequestIdFilter에서 생성되지 못한 경우 사용
     * 
     * @return String 새로운 Request ID
     */
    private String generateRequestId() {
        return "fallback-" + UUID.randomUUID().toString().substring(0, 8);
    }
}