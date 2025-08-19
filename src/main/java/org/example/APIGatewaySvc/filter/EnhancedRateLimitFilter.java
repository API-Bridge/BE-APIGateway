package org.example.APIGatewaySvc.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.service.KafkaProducerService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class EnhancedRateLimitFilter extends AbstractGatewayFilterFactory<EnhancedRateLimitFilter.Config> {

    private final KafkaProducerService kafkaProducerService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public EnhancedRateLimitFilter(KafkaProducerService kafkaProducerService,
                                  ReactiveRedisTemplate<String, String> redisTemplate) {
        super(Config.class);
        this.kafkaProducerService = kafkaProducerService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            String userId = extractUserId(exchange);
            String clientIp = getClientIp(exchange);
            
            // Rate Limit 체크 전 로깅
            log.debug("Rate Limit 체크 시작 - Path: {}, User: {}, IP: {}", path, userId, clientIp);
            
            return chain.filter(exchange).doOnSuccess(aVoid -> {
                // 요청 성공 시 로깅
                logRateLimitEvent(path, userId, clientIp, false, null, null);
            }).doOnError(throwable -> {
                // Rate Limit 에러 처리
                HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                if (statusCode != null && statusCode.value() == 429) {
                    logRateLimitEvent(path, userId, clientIp, true, "Rate limit exceeded", statusCode);
                }
            }).then(
                // Rate Limit 위반 체크 (응답 후 추가 정보 수집)
                Mono.fromRunnable(() -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    if (statusCode != null && statusCode.value() == 429) {
                        logRateLimitEvent(path, userId, clientIp, true, "Rate limit exceeded", statusCode);
                    } else {
                        logRateLimitEvent(path, userId, clientIp, false, "Request allowed", statusCode);
                    }
                })
            );
        };
    }

    private void logRateLimitEvent(String path, String userId, String clientIp, 
                                  boolean violated, String reason, HttpStatusCode statusCode) {
        try {
            // Redis에서 현재 Rate Limit 상태 조회 (비동기적으로 처리)
            String redisKey = "rate_limit:" + (userId != null ? userId : clientIp) + ":" + path;
            
            redisTemplate.opsForValue().get(redisKey + ":remaining")
                .defaultIfEmpty("0")
                .subscribe(remaining -> {
                    redisTemplate.opsForValue().get(redisKey + ":limit")
                        .defaultIfEmpty("10")
                        .subscribe(limit -> {
                            Map<String, Object> rateLimitEvent = new java.util.HashMap<>();
                            rateLimitEvent.put("eventType", violated ? "RATE_LIMIT_VIOLATED" : "RATE_LIMIT_CHECKED");
                            rateLimitEvent.put("path", path);
                            rateLimitEvent.put("userId", userId != null ? userId : "anonymous");
                            rateLimitEvent.put("clientIp", clientIp);
                            rateLimitEvent.put("violated", violated);
                            rateLimitEvent.put("reason", reason != null ? reason : "");
                            rateLimitEvent.put("limit", Integer.parseInt(limit));
                            rateLimitEvent.put("remaining", Integer.parseInt(remaining));
                            rateLimitEvent.put("statusCode", statusCode != null ? statusCode.value() : 200);
                            rateLimitEvent.put("timestamp", LocalDateTime.now().toString());

                            kafkaProducerService.sendRateLimitEvent(rateLimitEvent);
                            log.info("Rate Limit 이벤트 Kafka 전송 완료 - Path: {}, User: {}, Violated: {}", 
                                    path, userId, violated);
                        });
                }, error -> {
                    log.error("Redis에서 Rate Limit 정보 조회 실패: {}", error.getMessage());
                });
                
        } catch (Exception e) {
            log.error("Rate Limit 이벤트 로깅 실패: {}", e.getMessage(), e);
        }
    }

    private String extractUserId(org.springframework.web.server.ServerWebExchange exchange) {
        // JWT 토큰에서 사용자 ID 추출 로직
        try {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // JWT 파싱하여 사용자 ID 추출 (실제 구현에서는 JWT 라이브러리 사용)
                return "user_from_jwt"; // 임시값
            }
            return null;
        } catch (Exception e) {
            log.debug("사용자 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private String getClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    public static class Config {
        private String rateLimitKey = "default";
        private int replenishRate = 10;
        private int burstCapacity = 20;

        public String getRateLimitKey() {
            return rateLimitKey;
        }

        public void setRateLimitKey(String rateLimitKey) {
            this.rateLimitKey = rateLimitKey;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }
    }
}