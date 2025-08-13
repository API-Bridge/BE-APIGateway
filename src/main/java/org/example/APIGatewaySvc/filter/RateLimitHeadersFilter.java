package org.example.APIGatewaySvc.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

/**
 * Rate Limit 헤더 추가 PostFilter
 * RedisRateLimiter의 상태를 읽어 응답에 Rate Limit 정보 헤더 추가
 * 
 * 추가되는 헤더:
 * - X-RateLimit-Limit: 허용되는 총 요청 수
 * - X-RateLimit-Remaining: 남은 요청 수  
 * - X-RateLimit-Reset: Rate Limit 재설정 시간 (Unix timestamp)
 */
@Component
public class RateLimitHeadersFilter implements GlobalFilter, Ordered {
    // GlobalFilter 인터페이스를 구현하여 모든 요청에 대해 필터링 수행
    private static final Logger logger = LoggerFactory.getLogger(RateLimitHeadersFilter.class);
    
    // Rate Limit 헤더 상수
    private static final String RATE_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    
    // Redis 키 상수
    private static final String RATE_LIMIT_KEY_PREFIX = "request_rate_limiter.";
    private static final String TOKENS_KEY_SUFFIX = ".tokens";
    private static final String TIMESTAMP_KEY_SUFFIX = ".timestamp";
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RateLimitHeadersFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
            .then(Mono.defer(() -> addRateLimitHeaders(exchange)));
    }

    /**
     * Rate Limit 헤더 추가
     */
    private Mono<Void> addRateLimitHeaders(ServerWebExchange exchange) {
        try {
            // Rate Limiter 설정 정보 추출
            String rateLimitKey = extractRateLimitKey(exchange);
            if (rateLimitKey == null) {
                logger.debug("No rate limit key found for request");
                return Mono.empty();
            }

            // Redis에서 Rate Limit 상태 조회 및 헤더 추가
            return getRateLimitStatus(rateLimitKey)
                .doOnNext(status -> {
                    ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().add(RATE_LIMIT_HEADER, String.valueOf(status.limit));
                    response.getHeaders().add(RATE_LIMIT_REMAINING_HEADER, String.valueOf(status.remaining));
                    response.getHeaders().add(RATE_LIMIT_RESET_HEADER, String.valueOf(status.resetTime));
                    
                    logger.debug("Added rate limit headers: limit={}, remaining={}, reset={}", 
                        status.limit, status.remaining, status.resetTime);
                })
                .onErrorResume(error -> {
                    logger.warn("Failed to add rate limit headers: {}", error.getMessage());
                    return Mono.empty();
                })
                .then();

        } catch (Exception e) {
            logger.warn("Error processing rate limit headers: {}", e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Exchange에서 Rate Limit 키 추출
     */
    private String extractRateLimitKey(ServerWebExchange exchange) {
        // Gateway의 Rate Limiter에서 사용하는 키 추출
        Object rateLimitKey = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
        if (rateLimitKey != null) {
            return rateLimitKey.toString();
        }

        // 대체 방법: 요청 정보로부터 키 생성
        String userId = extractUserId(exchange);
        String routeId = extractRouteId(exchange);
        
        if (userId != null && routeId != null) {
            return routeId + "_" + userId;
        } else if (routeId != null) {
            // 익명 사용자의 경우 IP 기반
            String clientIp = getClientIpAddress(exchange);
            return routeId + "_" + clientIp;
        }
        
        return null;
    }

    /**
     * 사용자 ID 추출 (보안 컨텍스트에서)
     */
    private String extractUserId(ServerWebExchange exchange) {
        // 현재는 단순화하여 "anonymous" 반환
        // 실제로는 Security Context나 JWT에서 추출
        return "anonymous";
    }

    /**
     * 라우트 ID 추출
     */
    private String extractRouteId(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR))
            .map(route -> {
                try {
                    // Route 객체에서 ID 추출
                    return route.toString().split("\\[")[0];
                } catch (Exception e) {
                    return "unknown";
                }
            })
            .orElse("unknown");
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp.trim();
        }
        
        return exchange.getRequest().getRemoteAddress() != null ? 
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * Redis에서 Rate Limit 상태 조회
     */
    private Mono<RateLimitStatus> getRateLimitStatus(String key) {
        String tokensKey = RATE_LIMIT_KEY_PREFIX + key + TOKENS_KEY_SUFFIX;
        String timestampKey = RATE_LIMIT_KEY_PREFIX + key + TIMESTAMP_KEY_SUFFIX;

        return Mono.zip(
            redisTemplate.opsForValue().get(tokensKey).defaultIfEmpty("0"),
            redisTemplate.opsForValue().get(timestampKey).defaultIfEmpty("0")
        )
        .map(tuple -> {
            String tokens = tuple.getT1();
            String timestamp = tuple.getT2();
            
            int remaining = Integer.parseInt(tokens);
            long lastRefillTime = Long.parseLong(timestamp);
            
            // 기본 설정값 사용 (실제로는 설정에서 읽어야 함)
            int limit = 20; // burst-capacity 값
            long resetTime = calculateResetTime(lastRefillTime);
            
            return new RateLimitStatus(limit, Math.max(0, remaining), resetTime);
        })
        .onErrorReturn(new RateLimitStatus(20, 0, Instant.now().getEpochSecond() + 60));
    }

    /**
     * Rate Limit 재설정 시간 계산
     */
    private long calculateResetTime(long lastRefillTime) {
        // 1분 간격으로 토큰 보충 가정
        long refillIntervalSeconds = 60;
        long currentTime = Instant.now().getEpochSecond();
        
        if (lastRefillTime == 0) {
            return currentTime + refillIntervalSeconds;
        }
        
        long timeSinceLastRefill = currentTime - lastRefillTime;
        long nextRefillIn = refillIntervalSeconds - (timeSinceLastRefill % refillIntervalSeconds);
        
        return currentTime + nextRefillIn;
    }

    @Override
    public int getOrder() {
        // Rate Limiter 다음에 실행되어야 함
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    /**
     * Rate Limit 상태 정보 클래스
     */
    private static class RateLimitStatus {
        final int limit;
        final int remaining;  
        final long resetTime;

        RateLimitStatus(int limit, int remaining, long resetTime) {
            this.limit = limit;
            this.remaining = remaining;
            this.resetTime = resetTime;
        }
    }
}