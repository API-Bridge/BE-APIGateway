package org.example.APIGatewaySvc.filter;

import org.example.APIGatewaySvc.service.LoginAttemptService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 인증 결과를 추적하여 로그인 실패를 모니터링하는 필터
 * 인증 실패 시 LoginAttemptService를 통해 실패 횟수를 추적하고 필요시 차단 처리
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class LoginAttemptTrackingFilter implements GlobalFilter, Ordered {
    
    private final LoginAttemptService loginAttemptService;
    
    public LoginAttemptTrackingFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        // 인증이 필요하지 않은 경로는 건너뛰기
        String path = exchange.getRequest().getURI().getPath();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        // 클라이언트 IP 추출
        String clientIp = getClientIpAddress(exchange);
        
        return chain.filter(exchange)
            .then(Mono.defer(() -> {
                // 응답이 401 Unauthorized인 경우 인증 실패로 간주
                if (exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    return handleAuthenticationFailure(exchange, clientIp);
                }
                
                // 인증 성공 시 성공 처리
                return ReactiveSecurityContextHolder.getContext()
                    .map(securityContext -> securityContext.getAuthentication())
                    .cast(JwtAuthenticationToken.class)
                    .map(auth -> auth.getToken())
                    .map(jwt -> jwt.getClaimAsString("sub"))
                    .filter(userId -> userId != null && !userId.isEmpty())
                    .flatMap(userId -> loginAttemptService.recordLoginSuccess(userId))
                    .onErrorResume(e -> Mono.empty()) // 에러 시 무시
                    .then();
            }));
    }
    
    private Mono<Void> handleAuthenticationFailure(ServerWebExchange exchange, String clientIp) {
        // JWT 토큰에서 사용자 ID 추출 시도
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return extractUserIdFromToken(token)
                .flatMap(userId -> {
                    // 사용자별 실패 추적
                    Mono<Boolean> userBlocked = loginAttemptService.recordLoginFailure(userId, clientIp);
                    // IP별 실패 추적
                    Mono<Boolean> ipBlocked = loginAttemptService.recordIpLoginFailure(clientIp);
                    
                    return Mono.zip(userBlocked, ipBlocked)
                        .doOnNext(tuple -> {
                            boolean userWasBlocked = tuple.getT1();
                            boolean ipWasBlocked = tuple.getT2();
                            
                            if (userWasBlocked) {
                                logBlockEvent("USER", userId, "로그인 실패 횟수 초과");
                            }
                            if (ipWasBlocked) {
                                logBlockEvent("IP", clientIp, "로그인 실패 횟수 초과");
                            }
                        })
                        .then();
                })
                .onErrorResume(e -> {
                    // 토큰 파싱 실패 시 IP만 추적
                    return loginAttemptService.recordIpLoginFailure(clientIp)
                        .doOnNext(blocked -> {
                            if (blocked) {
                                logBlockEvent("IP", clientIp, "로그인 실패 횟수 초과 (토큰 파싱 실패)");
                            }
                        })
                        .then();
                });
        }
        
        // Authorization 헤더가 없는 경우 IP만 추적
        return loginAttemptService.recordIpLoginFailure(clientIp)
            .doOnNext(blocked -> {
                if (blocked) {
                    logBlockEvent("IP", clientIp, "로그인 실패 횟수 초과 (인증 헤더 없음)");
                }
            })
            .then();
    }
    
    private Mono<String> extractUserIdFromToken(String token) {
        try {
            // JWT 토큰을 파싱하여 사용자 ID 추출
            // 실제 구현에서는 JwtDecoder를 사용해야 하지만, 
            // 여기서는 간단히 base64 디코딩으로 처리
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                // JSON 파싱하여 sub 클레임 추출 (간단한 문자열 매칭 사용)
                if (payload.contains("\"sub\"")) {
                    int subIndex = payload.indexOf("\"sub\"");
                    int colonIndex = payload.indexOf(":", subIndex);
                    int startQuoteIndex = payload.indexOf("\"", colonIndex);
                    int endQuoteIndex = payload.indexOf("\"", startQuoteIndex + 1);
                    
                    if (startQuoteIndex != -1 && endQuoteIndex != -1) {
                        return Mono.just(payload.substring(startQuoteIndex + 1, endQuoteIndex));
                    }
                }
            }
        } catch (Exception e) {
            // 파싱 실패 시 빈 값 반환
        }
        
        return Mono.empty();
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
    
    private boolean isPublicPath(String path) {
        return path.startsWith("/public/") || 
               path.startsWith("/test/") ||
               path.startsWith("/auth/") ||
               path.startsWith("/oauth2/") ||
               path.startsWith("/login") ||
               path.startsWith("/static/") ||
               path.equals("/") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/webjars/");
    }
    
    private void logBlockEvent(String type, String identifier, String reason) {
        System.out.println(String.format("[BLOCK EVENT] Type: %s, ID: %s, Reason: %s, Time: %s", 
            type, identifier, reason, java.time.Instant.now()));
        // 실제 구현에서는 적절한 로깅 프레임워크 사용 권장
    }
    
    @Override
    public int getOrder() {
        // BlockCheckFilter 다음에 실행되도록 설정
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}