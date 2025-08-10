package org.example.APIGatewaySvc.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway Rate Limiting용 Key Resolver 설정
 * 요청자 식별을 위한 키 생성 전략 구현
 * 
 * 주요 기능:
 * - 사용자 ID 우선 추출 (JWT 토큰 sub 클레임 사용)
 * - 사용자 ID 없으면 클라이언트 IP 사용
 * - Rate Limit 우회 방지를 위한 안전한 키 생성
 * - 익명 사용자와 인증된 사용자 구분
 */
@Configuration
public class KeyResolverConfig {

    /**
     * 사용자 ID 기반 Key Resolver
     * JWT 토큰의 sub 클레임(사용자 ID)을 우선 사용하고, 
     * 없으면 클라이언트 IP 주소를 사용하여 Rate Limit 키 생성
     * 
     * @return KeyResolver Rate Limit용 키 해결자
     */
    @Bean("userKeyResolver")
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Spring Security Context에서 인증 정보 추출
            return exchange.getPrincipal()
                    .cast(JwtAuthenticationToken.class)
                    .map(JwtAuthenticationToken::getToken)
                    .map(Jwt::getSubject)  // JWT sub 클레임 (사용자 ID)
                    .filter(sub -> sub != null && !sub.trim().isEmpty())
                    .map(sub -> "user:" + sub)  // 사용자 ID 기반 키
                    .switchIfEmpty(
                            // 인증되지 않은 경우 IP 기반 키 사용
                            Mono.fromCallable(() -> {
                                String clientIP = getClientIP(exchange);
                                return "ip:" + clientIP;
                            })
                    );
        };
    }

    /**
     * IP 주소 기반 Key Resolver (백업용)
     * 사용자 인증이 불가능한 경우 클라이언트 IP만으로 Rate Limit 적용
     * 
     * @return KeyResolver IP 기반 키 해결자
     */
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.fromCallable(() -> {
            String clientIP = getClientIP(exchange);
            return "ip:" + clientIP;
        });
    }

    /**
     * 클라이언트 실제 IP 주소 추출
     * Proxy/Load Balancer 환경을 고려하여 정확한 클라이언트 IP 확인
     * Rate Limit 우회 방지를 위해 신뢰할 수 있는 헤더만 사용
     * 
     * @param exchange ServerWebExchange 웹 교환 객체
     * @return String 클라이언트 IP 주소
     */
    private String getClientIP(org.springframework.web.server.ServerWebExchange exchange) {
        // X-Forwarded-For 헤더 확인 (프록시 환경)
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // 첫 번째 IP만 사용 (실제 클라이언트 IP)
            String firstIP = xForwardedFor.split(",")[0].trim();
            if (isValidIP(firstIP)) {
                return firstIP;
            }
        }
        
        // X-Real-IP 헤더 확인 (Nginx 등)
        String xRealIP = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.trim().isEmpty() && isValidIP(xRealIP)) {
            return xRealIP;
        }
        
        // CF-Connecting-IP 헤더 확인 (Cloudflare)
        String cfConnectingIP = exchange.getRequest().getHeaders().getFirst("CF-Connecting-IP");
        if (cfConnectingIP != null && !cfConnectingIP.trim().isEmpty() && isValidIP(cfConnectingIP)) {
            return cfConnectingIP;
        }
        
        // 직접 연결된 클라이언트 IP (RemoteAddress)
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        // 기본값 (IP 확인 불가능한 경우)
        return "unknown";
    }

    /**
     * IP 주소 형식 검증
     * 잘못된 형식의 IP나 내부망 IP 등을 필터링
     * 
     * @param ip IP 주소 문자열
     * @return boolean 유효한 IP 주소 여부
     */
    private boolean isValidIP(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        // 기본 IP 형식 검증 (간단한 패턴)
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        if (!ip.matches(ipPattern)) {
            return false;
        }
        
        // 로컬호스트 및 사설망 IP 제외 (선택사항)
        return !ip.startsWith("127.") && 
               !ip.startsWith("10.") && 
               !ip.startsWith("192.168.") &&
               !ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }
}