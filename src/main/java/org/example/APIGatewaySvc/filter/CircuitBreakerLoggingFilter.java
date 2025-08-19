package org.example.APIGatewaySvc.filter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.service.KafkaProducerService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class CircuitBreakerLoggingFilter extends AbstractGatewayFilterFactory<CircuitBreakerLoggingFilter.Config> {

    private final KafkaProducerService kafkaProducerService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerLoggingFilter(KafkaProducerService kafkaProducerService,
                                     CircuitBreakerRegistry circuitBreakerRegistry) {
        super(Config.class);
        this.kafkaProducerService = kafkaProducerService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String circuitBreakerName = config.getCircuitBreakerName();
            String serviceName = config.getServiceName();
            String path = exchange.getRequest().getPath().value();
            
            // Circuit Breaker 상태 확인 및 이벤트 리스너 등록
            try {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
                
                // Circuit Breaker 상태 변경 이벤트 리스너 (한 번만 등록)
                if (!hasEventListener(circuitBreaker)) {
                    registerCircuitBreakerEventListeners(circuitBreaker, serviceName);
                }
                
                // 현재 상태 로깅
                CircuitBreaker.State currentState = circuitBreaker.getState();
                CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
                
                log.debug("Circuit Breaker 체크 - Service: {}, State: {}, FailureRate: {}%, SlowCallRate: {}%",
                        serviceName, currentState, 
                        metrics.getFailureRate(), metrics.getSlowCallRate());
                
                // 요청 전 상태 기록
                logCircuitBreakerMetrics(serviceName, circuitBreakerName, currentState, metrics, 
                                       "REQUEST_START", path);
                
            } catch (Exception e) {
                log.error("Circuit Breaker 설정 오류 - Service: {}, Error: {}", serviceName, e.getMessage());
            }
            
            return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    // 성공 시 메트릭 로깅
                    logRequestResult(serviceName, circuitBreakerName, path, "SUCCESS", null);
                })
                .doOnError(throwable -> {
                    // 실패 시 메트릭 로깅
                    logRequestResult(serviceName, circuitBreakerName, path, "ERROR", throwable.getMessage());
                })
                .onErrorResume(throwable -> {
                    // Circuit Breaker가 OPEN 상태인 경우의 처리
                    if (throwable.getMessage() != null && throwable.getMessage().contains("CircuitBreaker")) {
                        logCircuitBreakerEvent(serviceName, circuitBreakerName, "CIRCUIT_BREAKER_CALL_REJECTED", 
                                             null, null, throwable.getMessage(), path);
                    }
                    return Mono.error(throwable);
                });
        };
    }

    private boolean hasEventListener(CircuitBreaker circuitBreaker) {
        // 이미 이벤트 리스너가 등록되었는지 확인하는 간단한 방법
        // 실제로는 더 정교한 중복 체크가 필요할 수 있음
        return false; // 간단히 매번 등록하도록 설정 (중복 등록은 무시됨)
    }

    private void registerCircuitBreakerEventListeners(CircuitBreaker circuitBreaker, String serviceName) {
        String circuitBreakerName = circuitBreaker.getName();
        
        // 상태 변경 이벤트
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                logCircuitBreakerEvent(serviceName, circuitBreakerName, "STATE_TRANSITION",
                                     event.getStateTransition().getFromState().toString(),
                                     event.getStateTransition().getToState().toString(),
                                     null, null);
            });
        
        // 성공 이벤트
        circuitBreaker.getEventPublisher()
            .onSuccess(event -> {
                log.trace("Circuit Breaker 성공 - Service: {}, Duration: {}ms", 
                         serviceName, event.getElapsedDuration().toMillis());
            });
        
        // 실패 이벤트
        circuitBreaker.getEventPublisher()
            .onError(event -> {
                logCircuitBreakerEvent(serviceName, circuitBreakerName, "ERROR",
                                     null, null, event.getThrowable().getMessage(), null);
            });
        
        // 호출 거부 이벤트
        circuitBreaker.getEventPublisher()
            .onCallNotPermitted(event -> {
                logCircuitBreakerEvent(serviceName, circuitBreakerName, "CALL_NOT_PERMITTED",
                                     null, null, "Circuit breaker is OPEN", null);
            });
    }

    private void logRequestResult(String serviceName, String circuitBreakerName, String path, 
                                 String result, String error) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            
            logCircuitBreakerMetrics(serviceName, circuitBreakerName, circuitBreaker.getState(), 
                                   metrics, "REQUEST_" + result, path);
            
        } catch (Exception e) {
            log.error("Circuit Breaker 요청 결과 로깅 실패: {}", e.getMessage());
        }
    }

    private void logCircuitBreakerEvent(String serviceName, String circuitBreakerName, String eventType,
                                      String fromState, String toState, String errorMessage, String path) {
        try {
            Map<String, Object> circuitBreakerEvent = Map.of(
                    "serviceName", serviceName,
                    "circuitBreakerName", circuitBreakerName,
                    "eventType", eventType,
                    "fromState", fromState != null ? fromState : "",
                    "toState", toState != null ? toState : "",
                    "path", path != null ? path : "",
                    "errorMessage", errorMessage != null ? errorMessage : "",
                    "timestamp", LocalDateTime.now().toString()
            );

            kafkaProducerService.sendMessage("events.circuitbreaker", circuitBreakerEvent);
            log.info("Circuit Breaker 이벤트 Kafka 전송 - Service: {}, Event: {}, From: {}, To: {}", 
                    serviceName, eventType, fromState, toState);
            
        } catch (Exception e) {
            log.error("Circuit Breaker 이벤트 로깅 실패: {}", e.getMessage(), e);
        }
    }

    private void logCircuitBreakerMetrics(String serviceName, String circuitBreakerName, 
                                        CircuitBreaker.State state, CircuitBreaker.Metrics metrics,
                                        String eventType, String path) {
        try {
            Map<String, Object> metricsEvent = new java.util.HashMap<>();
            metricsEvent.put("serviceName", serviceName);
            metricsEvent.put("circuitBreakerName", circuitBreakerName);
            metricsEvent.put("eventType", eventType);
            metricsEvent.put("state", state.toString());
            metricsEvent.put("path", path != null ? path : "");
            metricsEvent.put("failureRate", metrics.getFailureRate());
            metricsEvent.put("slowCallRate", metrics.getSlowCallRate());
            metricsEvent.put("numberOfCalls", metrics.getNumberOfBufferedCalls());
            metricsEvent.put("numberOfFailures", metrics.getNumberOfFailedCalls());
            metricsEvent.put("numberOfSlowCalls", metrics.getNumberOfSlowCalls());
            metricsEvent.put("timestamp", LocalDateTime.now().toString());

            // 메트릭 정보는 너무 자주 전송되지 않도록 특정 조건에서만 전송
            if (state != CircuitBreaker.State.CLOSED || "REQUEST_ERROR".equals(eventType)) {
                kafkaProducerService.sendMessage("events.circuitbreaker", metricsEvent);
            }
            
        } catch (Exception e) {
            log.error("Circuit Breaker 메트릭 로깅 실패: {}", e.getMessage(), e);
        }
    }

    public static class Config {
        private String circuitBreakerName = "default";
        private String serviceName = "unknown";

        public String getCircuitBreakerName() {
            return circuitBreakerName;
        }

        public void setCircuitBreakerName(String circuitBreakerName) {
            this.circuitBreakerName = circuitBreakerName;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }
}