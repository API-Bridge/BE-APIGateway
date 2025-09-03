package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
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
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "503", description = "서비스를 일시적으로 사용할 수 없습니다",
            content = @Content(mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public Mono<ResponseEntity<ProblemDetail>> serviceUnavailable(@Parameter(hidden = true) ServerWebExchange exchange) {
        String requestId = generateRequestId();
        String detail = "The requested service is temporarily unavailable. Please try again later.";

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problemDetail.setTitle("Service temporarily unavailable");
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setInstance(URI.create(requestId));

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(problemDetail));
    }

    /**
     * 특정 서비스별 Fallback 처리
     * 서비스 이름에 따라 구체적인 에러 메시지 제공
     *
     * @param serviceName 장애가 발생한 서비스 이름
     * @param exchange ServerWebExchange 웹 교환 객체
     * @return Mono<Void> 서비스별 맞춤 에러 응답
     */
    // GET 외에 POST, PUT, DELETE 등 모든 메서드를 처리할 수 있도록 @RequestMapping으로 변경
    @RequestMapping("/{serviceName}")
    public Mono<ResponseEntity<ProblemDetail>> serviceFallback(@PathVariable String serviceName, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = generateRequestId();
        String detail = generateServiceSpecificMessage(serviceName, request.getMethod().name());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problemDetail.setTitle("Service temporarily unavailable");
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setInstance(URI.create(requestId));

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(problemDetail));
    }

    /**
     * 서비스별 구체적인 에러 메시지 생성
     * 사용자가 이해하기 쉬운 맞춤형 메시지 제공
     *
     * @param serviceName 서비스 이름
     * @param method
     * @return String 서비스별 에러 메시지
     */
    private String generateServiceSpecificMessage(String serviceName, String method) {
        return switch (serviceName.toLowerCase()) {
            case "user-service" ->
                    String.format("User management service is temporarily unavailable for %s requests. Login and profile features may not work.", method);
            case "api-management", "apimgmt" ->
                    String.format("API management service is temporarily unavailable for %s requests. API configuration features may not work.", method);
            case "custom-api-management", "customapi" ->
                    String.format("Custom API service is temporarily unavailable for %s requests. Custom API features may not work.", method);
            case "ai-feature", "aifeature" ->
                    String.format("AI feature service is temporarily unavailable for %s requests. AI-powered features may not work.", method);
            case "system-management", "sysmgmt" ->
                    String.format("System management service is temporarily unavailable for %s requests. Admin features may not work.", method);
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