package org.example.APIGatewaySvc.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * 차단 관리 서비스
 * Redis 기반 차단 기능의 비즈니스 로직을 처리
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class BlockService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    public BlockService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 사용자 차단
     * @param userId 사용자 ID
     * @param duration 차단 기간 (null이면 영구차단)
     * @param reason 차단 사유
     */
    public Mono<Void> blockUser(String userId, Duration duration, String reason) {
        String key = "blocked:user:" + userId;
        String value = reason != null ? reason : "차단됨";
        
        if (duration != null) {
            return redisTemplate.opsForValue().set(key, value, duration).then();
        } else {
            return redisTemplate.opsForValue().set(key, value).then();
        }
    }
    
    /**
     * IP 차단
     * @param ipAddress IP 주소
     * @param duration 차단 기간 (null이면 영구차단)
     * @param reason 차단 사유
     */
    public Mono<Void> blockIp(String ipAddress, Duration duration, String reason) {
        String key = "blocked:ip:" + ipAddress;
        String value = reason != null ? reason : "차단됨";
        
        if (duration != null) {
            return redisTemplate.opsForValue().set(key, value, duration).then();
        } else {
            return redisTemplate.opsForValue().set(key, value).then();
        }
    }
    
    /**
     * API 키 차단
     * @param apiKey API 키
     * @param duration 차단 기간 (null이면 영구차단)
     * @param reason 차단 사유
     */
    public Mono<Void> blockApiKey(String apiKey, Duration duration, String reason) {
        String key = "blocked:key:" + apiKey;
        String value = reason != null ? reason : "차단됨";
        
        if (duration != null) {
            return redisTemplate.opsForValue().set(key, value, duration).then();
        } else {
            return redisTemplate.opsForValue().set(key, value).then();
        }
    }
    
    /**
     * 사용자 차단 해제
     * @param userId 사용자 ID
     */
    public Mono<Boolean> unblockUser(String userId) {
        String key = "blocked:user:" + userId;
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }
    
    /**
     * IP 차단 해제
     * @param ipAddress IP 주소
     */
    public Mono<Boolean> unblockIp(String ipAddress) {
        String key = "blocked:ip:" + ipAddress;
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }
    
    /**
     * API 키 차단 해제
     * @param apiKey API 키
     */
    public Mono<Boolean> unblockApiKey(String apiKey) {
        String key = "blocked:key:" + apiKey;
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }
    
    /**
     * 차단 상태 확인
     * @param userId 사용자 ID
     * @return 차단 정보 (차단되지 않으면 null)
     */
    public Mono<BlockInfo> checkUserBlock(String userId) {
        String key = "blocked:user:" + userId;
        return checkBlock(key, "USER");
    }
    
    /**
     * IP 차단 상태 확인
     * @param ipAddress IP 주소
     * @return 차단 정보 (차단되지 않으면 null)
     */
    public Mono<BlockInfo> checkIpBlock(String ipAddress) {
        String key = "blocked:ip:" + ipAddress;
        return checkBlock(key, "IP");
    }
    
    /**
     * API 키 차단 상태 확인
     * @param apiKey API 키
     * @return 차단 정보 (차단되지 않으면 null)
     */
    public Mono<BlockInfo> checkApiKeyBlock(String apiKey) {
        String key = "blocked:key:" + apiKey;
        return checkBlock(key, "API_KEY");
    }
    
    private Mono<BlockInfo> checkBlock(String key, String type) {
        return redisTemplate.hasKey(key)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.empty();
                }
                
                return redisTemplate.opsForValue().get(key)
                    .zipWith(redisTemplate.getExpire(key))
                    .map(tuple -> {
                        String reason = tuple.getT1();
                        Duration ttl = tuple.getT2();
                        
                        Instant expiresAt = null;
                        if (!ttl.isNegative() && !ttl.isZero()) {
                            expiresAt = Instant.now().plus(ttl);
                        }
                        
                        return new BlockInfo(type, reason, expiresAt);
                    });
            });
    }
    
    /**
     * 차단 정보를 담는 클래스
     */
    public static class BlockInfo {
        private final String type;
        private final String reason;
        private final Instant expiresAt;
        
        public BlockInfo(String type, String reason, Instant expiresAt) {
            this.type = type;
            this.reason = reason;
            this.expiresAt = expiresAt;
        }
        
        public String getType() { return type; }
        public String getReason() { return reason; }
        public Instant getExpiresAt() { return expiresAt; }
        public boolean isPermanent() { return expiresAt == null; }
    }
}