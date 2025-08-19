package org.example.APIGatewaySvc.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * 로그인 시도 모니터링 및 차단 서비스
 * Redis를 사용하여 로그인 실패 횟수를 캐싱하고 임계값 초과 시 자동 차단
 * 
 * 캐싱 전략:
 * - 로그인 실패 횟수: 15분 윈도우로 캐싱
 * - IP 실패 횟수: 15분 윈도우로 캐싱  
 * - 메모리 효율성을 위한 자동 만료 설정
 * - 파이프라이닝을 통한 성능 최적화
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class LoginAttemptService {
    
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_IP_ATTEMPTS = 10; // IP는 더 높은 임계값
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15); // 15분 윈도우
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(30); // 30분 차단
    private static final String ATTEMPT_KEY_PREFIX = "login_attempts:";
    private static final String IP_ATTEMPT_KEY_PREFIX = "login_attempts:ip:";
    private static final String BLOCK_KEY_PREFIX = "blocked:user:";
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final BlockService blockService;
    
    public LoginAttemptService(ReactiveRedisTemplate<String, String> redisTemplate, BlockService blockService) {
        this.redisTemplate = redisTemplate;
        this.blockService = blockService;
    }
    
    /**
     * 로그인 실패 기록
     * @param userId 사용자 ID
     * @param ipAddress IP 주소
     * @return 차단 여부
     */
    public Mono<Boolean> recordLoginFailure(String userId, String ipAddress) {
        String attemptKey = ATTEMPT_KEY_PREFIX + userId;
        
        return redisTemplate.opsForValue()
            .increment(attemptKey)
            .flatMap(attempts -> {
                // 첫 번째 실패 시 TTL 설정
                if (attempts == 1) {
                    return redisTemplate.expire(attemptKey, ATTEMPT_WINDOW)
                        .thenReturn(attempts);
                }
                return Mono.just(attempts);
            })
            .flatMap(attempts -> {
                if (attempts >= MAX_ATTEMPTS) {
                    // 최대 시도 횟수 초과 시 사용자 차단
                    String reason = String.format("로그인 %d회 실패 (IP: %s)", attempts, ipAddress);
                    return blockService.blockUser(userId, BLOCK_DURATION, reason)
                        .then(redisTemplate.delete(attemptKey)) // 차단 후 시도 횟수 초기화
                        .thenReturn(true);
                }
                return Mono.just(false);
            });
    }
    
    /**
     * 로그인 성공 시 실패 횟수 초기화
     * @param userId 사용자 ID
     */
    public Mono<Void> recordLoginSuccess(String userId) {
        String attemptKey = ATTEMPT_KEY_PREFIX + userId;
        return redisTemplate.delete(attemptKey).then();
    }
    
    /**
     * 현재 로그인 시도 횟수 조회
     * @param userId 사용자 ID
     * @return 현재 시도 횟수
     */
    public Mono<Integer> getAttemptCount(String userId) {
        String attemptKey = ATTEMPT_KEY_PREFIX + userId;
        return redisTemplate.opsForValue()
            .get(attemptKey)
            .map(Integer::parseInt)
            .defaultIfEmpty(0);
    }
    
    /**
     * 남은 시도 횟수 조회
     * @param userId 사용자 ID
     * @return 남은 시도 횟수
     */
    public Mono<Integer> getRemainingAttempts(String userId) {
        return getAttemptCount(userId)
            .map(attempts -> Math.max(0, MAX_ATTEMPTS - attempts));
    }
    
    /**
     * 시도 윈도우 만료 시간 조회
     * @param userId 사용자 ID
     * @return 만료 시간 (없으면 null)
     */
    public Mono<Instant> getAttemptWindowExpiry(String userId) {
        String attemptKey = ATTEMPT_KEY_PREFIX + userId;
        return redisTemplate.getExpire(attemptKey)
            .filter(ttl -> !ttl.isNegative())
            .map(ttl -> Instant.now().plus(ttl))
            .defaultIfEmpty(null);
    }
    
    /**
     * IP 기반 로그인 실패 추적 및 캐싱
     * @param ipAddress IP 주소
     * @return 차단 여부
     */
    public Mono<Boolean> recordIpLoginFailure(String ipAddress) {
        String attemptKey = IP_ATTEMPT_KEY_PREFIX + ipAddress;
        
        return redisTemplate.opsForValue()
            .increment(attemptKey)
            .flatMap(attempts -> {
                if (attempts == 1) {
                    return redisTemplate.expire(attemptKey, ATTEMPT_WINDOW)
                        .thenReturn(attempts);
                }
                return Mono.just(attempts);
            })
            .flatMap(attempts -> {
                if (attempts >= MAX_IP_ATTEMPTS) {
                    String reason = String.format("IP에서 로그인 %d회 실패", attempts);
                    return blockService.blockIp(ipAddress, BLOCK_DURATION, reason)
                        .then(redisTemplate.delete(attemptKey))
                        .thenReturn(true);
                }
                return Mono.just(false);
            });
    }
    
    /**
     * IP별 현재 로그인 시도 횟수 조회 (캐시에서)
     * @param ipAddress IP 주소
     * @return 현재 시도 횟수
     */
    public Mono<Integer> getIpAttemptCount(String ipAddress) {
        String attemptKey = IP_ATTEMPT_KEY_PREFIX + ipAddress;
        return redisTemplate.opsForValue()
            .get(attemptKey)
            .map(Integer::parseInt)
            .defaultIfEmpty(0);
    }
    
    /**
     * 캐시에서 로그인 시도 통계 조회
     * @param userId 사용자 ID
     * @return 시도 통계 정보
     */
    public Mono<LoginAttemptStats> getLoginAttemptStats(String userId) {
        String attemptKey = ATTEMPT_KEY_PREFIX + userId;
        
        return Mono.zip(
            redisTemplate.opsForValue().get(attemptKey).map(Integer::parseInt).defaultIfEmpty(0),
            redisTemplate.getExpire(attemptKey).defaultIfEmpty(Duration.ZERO),
            getRemainingAttempts(userId)
        ).map(tuple -> new LoginAttemptStats(
            userId,
            tuple.getT1(), // current attempts
            tuple.getT3(), // remaining attempts
            (!tuple.getT2().isNegative() && !tuple.getT2().isZero()) ? Instant.now().plus(tuple.getT2()) : null // window expiry
        ));
    }
    
    /**
     * 로그인 시도 통계를 담는 클래스
     */
    public static class LoginAttemptStats {
        private final String userId;
        private final int currentAttempts;
        private final int remainingAttempts;
        private final Instant windowExpiry;
        
        public LoginAttemptStats(String userId, int currentAttempts, int remainingAttempts, Instant windowExpiry) {
            this.userId = userId;
            this.currentAttempts = currentAttempts;
            this.remainingAttempts = remainingAttempts;
            this.windowExpiry = windowExpiry;
        }
        
        public String getUserId() { return userId; }
        public int getCurrentAttempts() { return currentAttempts; }
        public int getRemainingAttempts() { return remainingAttempts; }
        public Instant getWindowExpiry() { return windowExpiry; }
        public boolean isBlocked() { return remainingAttempts <= 0; }
    }
}