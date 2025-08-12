package org.example.APIGatewaySvc.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// 요청이 백엔드로 전달되기 전에 차단 여부를 확인하는 필터
// 차단 필터
// - IP, API 키, 사용자 ID를 Redis에서 조회하여 차단 여부 결정
// - 차단된 경우 403 Forbidden 응답 반환
// - 차단되지 않은 경우 다음 필터로 요청 전달
@Component
public class BlockCheckFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public BlockCheckFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        // 1. IP 추출 (X-Forwarded-For 헤더 고려)
        String ip = getClientIpAddress(exchange);
        
        // 2. API 키 추출 (예시: 'X-Api-Key' 헤더)
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        
        // 3. 사용자 ID 추출 (JWT claim)
        Mono<String> userIdMono = ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    Authentication authentication = securityContext.getAuthentication();
                    if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                        Jwt jwt = (Jwt) authentication.getPrincipal();
                        return jwt.getClaimAsString("sub");
                    }
                    return "";
                })
                .defaultIfEmpty("");

        // Redis 차단 목록 조회 (병렬 처리로 성능 최적화)
        return userIdMono.flatMap(userId -> {
            // TTL 기반 차단 확인
            Mono<BlockInfo> ipBlockInfo = checkBlockWithTTL("blocked:ip:" + ip, "IP");
            Mono<BlockInfo> apiKeyBlockInfo = apiKey != null ? 
                checkBlockWithTTL("blocked:key:" + apiKey, "API_KEY") : 
                Mono.just(BlockInfo.notBlocked());
            Mono<BlockInfo> userBlockInfo = !userId.isEmpty() ? 
                checkBlockWithTTL("blocked:user:" + userId, "USER") : 
                Mono.just(BlockInfo.notBlocked());

            return Mono.zip(ipBlockInfo, apiKeyBlockInfo, userBlockInfo)
                .flatMap(tuple -> {
                    BlockInfo ipBlock = tuple.getT1();
                    BlockInfo apiKeyBlock = tuple.getT2();
                    BlockInfo userBlock = tuple.getT3();
                    
                    // 차단된 것이 있는지 확인
                    BlockInfo blockedInfo = null;
                    if (ipBlock.isBlocked()) blockedInfo = ipBlock;
                    else if (apiKeyBlock.isBlocked()) blockedInfo = apiKeyBlock;
                    else if (userBlock.isBlocked()) blockedInfo = userBlock;
                    
                    if (blockedInfo != null) {
                        return createBlockedResponse(exchange.getResponse(), blockedInfo);
                    }
                    return chain.filter(exchange);
                });
        });
    }

    private String getClientIpAddress(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null ? 
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private Mono<BlockInfo> checkBlockWithTTL(String key, String type) {
        return redisTemplate.hasKey(key)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(BlockInfo.notBlocked());
                }
                
                // TTL 확인
                return redisTemplate.getExpire(key)
                    .map(ttl -> {
                        if (!ttl.isNegative() && !ttl.isZero()) {
                            // TTL이 있는 경우 만료시간 계산
                            Instant expiresAt = Instant.now().plus(ttl);
                            return new BlockInfo(true, type, "Temporarily blocked", expiresAt);
                        } else {
                            // 영구 차단
                            return new BlockInfo(true, type, "Permanently blocked", null);
                        }
                    });
            });
    }

    private static class BlockInfo {
        private final boolean blocked;
        private final String type;
        private final String reason;
        private final Instant expiresAt;
        
        public BlockInfo(boolean blocked, String type, String reason, Instant expiresAt) {
            this.blocked = blocked;
            this.type = type;
            this.reason = reason;
            this.expiresAt = expiresAt;
        }
        
        public static BlockInfo notBlocked() {
            return new BlockInfo(false, null, null, null);
        }
        
        public boolean isBlocked() { return blocked; }
        public String getType() { return type; }
        public String getReason() { return reason; }
        public Instant getExpiresAt() { return expiresAt; }
    }

    private Mono<Void> createBlockedResponse(ServerHttpResponse response, BlockInfo blockInfo) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        // 상세한 차단 정보를 포함한 JSON 응답 생성
        String expiresAtStr = blockInfo.getExpiresAt() != null ? 
            "\"" + blockInfo.getExpiresAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\"" : 
            "null";
            
        String jsonResponse = String.format(
            "{\"success\":false,\"data\":null,\"error\":{\"code\":\"BLOCKED\",\"message\":\"%s\",\"details\":{\"type\":\"%s\",\"reason\":\"%s\",\"expiresAt\":%s}},\"meta\":{\"requestId\":\"%s\",\"timestamp\":\"%s\",\"durationMs\":0}}",
            blockInfo.getType() + " 차단됨",
            blockInfo.getType(),
            blockInfo.getReason(),
            expiresAtStr,
            UUID.randomUUID(),
            Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 필터 체인에서 가장 먼저 실행
    }
}