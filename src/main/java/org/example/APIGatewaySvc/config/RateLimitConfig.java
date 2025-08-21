package org.example.APIGatewaySvc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring Cloud Gateway Rate Limiting 설정
 * Redis를 백엔드로 사용하는 분산 Rate Limiter 구현
 * 
 * 주요 기능:
 * - 사용자별/IP별 요청 제한 (Token Bucket Algorithm)
 * - Redis Cluster 지원 (분산 환경)
 * - 서비스별 다른 Rate Limit 정책 적용 가능
 * - 실시간 Rate Limit 상태 모니터링
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class RateLimitConfig {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

    @Value("${rate-limit.default.replenish-rate:1}")
    private int defaultReplenishRate;

    @Value("${rate-limit.default.burst-capacity:3}")
    private int defaultBurstCapacity;

    @Value("${rate-limit.default.requested-tokens:1}")
    private int defaultRequestedTokens;

    /**
     * 기본 Redis Rate Limiter 설정
     * Token Bucket 알고리즘을 사용한 Rate Limiting
     * 
     * @return RedisRateLimiter 기본 Rate Limiter
     */
    @Bean("defaultRedisRateLimiter")
    @Primary
    public RedisRateLimiter defaultRedisRateLimiter() {
        // application.yml 설정값 사용
        RedisRateLimiter rateLimiter = new RedisRateLimiter(
                defaultReplenishRate,    // application.yml에서 읽어온 값
                defaultBurstCapacity,    // application.yml에서 읽어온 값
                defaultRequestedTokens   // application.yml에서 읽어온 값
        );
        
        // 디버깅을 위한 로깅
        logger.info("DefaultRedisRateLimiter created: replenishRate={}, burstCapacity={}, requestedTokens={}", 
                   defaultReplenishRate, defaultBurstCapacity, defaultRequestedTokens);
        
        return rateLimiter;
    }

    /**
     * 사용자 서비스용 Rate Limiter (더 관대한 정책)
     * 로그인, 프로필 조회 등 자주 사용되는 API용
     * 
     * @return RedisRateLimiter 사용자 서비스용 Rate Limiter
     */
    @Bean("userServiceRateLimiter")
    public RedisRateLimiter userServiceRateLimiter() {
        return new RedisRateLimiter(
                20,  // 초당 20개 요청
                40,  // 버스트 40개 허용
                1    // 요청당 1토큰
        );
    }

    /**
     * AI 서비스용 Rate Limiter (더 엄격한 정책)
     * 리소스 집약적인 AI 기능에 대한 제한
     * 
     * @return RedisRateLimiter AI 서비스용 Rate Limiter
     */
    @Bean("aiServiceRateLimiter") 
    public RedisRateLimiter aiServiceRateLimiter() {
        return new RedisRateLimiter(
                5,   // 초당 5개 요청
                10,  // 버스트 10개 허용
                2    // 요청당 2토큰 (더 비싼 비용)
        );
    }

    /**
     * 관리 서비스용 Rate Limiter (중간 정책)
     * API 관리, 시스템 관리 등 관리자 기능용
     * 
     * @return RedisRateLimiter 관리 서비스용 Rate Limiter
     */
    @Bean("managementServiceRateLimiter")
    public RedisRateLimiter managementServiceRateLimiter() {
        return new RedisRateLimiter(
                15,  // 초당 15개 요청
                30,  // 버스트 30개 허용
                1    // 요청당 1토큰
        );
    }

}