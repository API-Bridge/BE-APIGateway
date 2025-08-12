package org.example.APIGatewaySvc.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/public/circuit-breaker")
@Tag(name = "Circuit Breaker Monitoring", description = "서킷브레이커 상태 모니터링 API")
public class CircuitBreakerController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerController(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @GetMapping("/status")
    @Operation(summary = "전체 서킷브레이커 상태 조회", description = "모든 서킷브레이커의 현재 상태를 조회합니다")
    @ApiResponse(responseCode = "200", description = "서킷브레이커 상태 조회 성공")
    public ResponseEntity<Map<String, Object>> getAllCircuitBreakerStatus() {
        Map<String, Object> allStatus = new HashMap<>();
        
        Map<String, Object> circuitBreakers = circuitBreakerRegistry.getAllCircuitBreakers()
            .stream()
            .collect(Collectors.toMap(
                CircuitBreaker::getName,
                this::getCircuitBreakerDetails
            ));

        // 요약 정보
        Map<String, Integer> summary = new HashMap<>();
        summary.put("total", circuitBreakers.size());
        summary.put("closed", (int) circuitBreakers.values().stream()
            .filter(cb -> "CLOSED".equals(((Map<String, Object>) cb).get("state")))
            .count());
        summary.put("open", (int) circuitBreakers.values().stream()
            .filter(cb -> "OPEN".equals(((Map<String, Object>) cb).get("state")))
            .count());
        summary.put("halfOpen", (int) circuitBreakers.values().stream()
            .filter(cb -> "HALF_OPEN".equals(((Map<String, Object>) cb).get("state")))
            .count());

        allStatus.put("summary", summary);
        allStatus.put("circuitBreakers", circuitBreakers);
        allStatus.put("timestamp", Instant.now());

        return ResponseEntity.ok(allStatus);
    }

    @GetMapping("/status/{name}")
    @Operation(summary = "특정 서킷브레이커 상태 조회", description = "지정된 이름의 서킷브레이커 상세 상태를 조회합니다")
    @ApiResponse(responseCode = "200", description = "서킷브레이커 상태 조회 성공")
    @ApiResponse(responseCode = "404", description = "해당 이름의 서킷브레이커를 찾을 수 없음")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus(
            @PathVariable @Parameter(description = "서킷브레이커 이름") String name) {
        
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            Map<String, Object> status = getCircuitBreakerDetails(circuitBreaker);
            status.put("timestamp", Instant.now());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Circuit breaker not found");
            error.put("name", name);
            error.put("availableCircuitBreakers", 
                circuitBreakerRegistry.getAllCircuitBreakers().stream()
                    .map(CircuitBreaker::getName)
                    .collect(Collectors.toSet()));
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> getCircuitBreakerDetails(CircuitBreaker circuitBreaker) {
        Map<String, Object> details = new HashMap<>();
        
        details.put("name", circuitBreaker.getName());
        details.put("state", circuitBreaker.getState().toString());
        
        // 메트릭 정보
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        details.put("failureRate", metrics.getFailureRate());
        details.put("slowCallRate", metrics.getSlowCallRate());
        details.put("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls());
        details.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
        details.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
        details.put("numberOfSlowCalls", metrics.getNumberOfSlowCalls());
        
        // 설정 정보
        try {
            var config = circuitBreaker.getCircuitBreakerConfig();
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("failureRateThreshold", config.getFailureRateThreshold());
            configMap.put("slowCallRateThreshold", config.getSlowCallRateThreshold());
            configMap.put("slowCallDurationThreshold", config.getSlowCallDurationThreshold().toMillis());
            configMap.put("minimumNumberOfCalls", config.getMinimumNumberOfCalls());
            configMap.put("permittedNumberOfCallsInHalfOpenState", config.getPermittedNumberOfCallsInHalfOpenState());
            details.put("config", configMap);
        } catch (Exception e) {
            // 일부 설정 정보를 가져올 수 없는 경우 무시
            details.put("config", "Configuration details not available");
        }
        
        return details;
    }
}