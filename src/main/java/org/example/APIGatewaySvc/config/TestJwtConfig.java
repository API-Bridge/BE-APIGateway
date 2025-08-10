package org.example.APIGatewaySvc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 테스트용 JWT 설정
 * 로컬 개발 및 테스트 환경에서 실제 Auth0 없이 JWT 토큰 검증을 위한 설정
 * 
 * 사용 방법:
 * application.yml에서 jwt.test-mode: true로 설정하면 활성화됨
 * 이 경우 실제 Auth0 JWKS 엔드포인트 대신 로컬 HS256 키를 사용함
 */
@Configuration
@ConditionalOnProperty(name = "jwt.test-mode", havingValue = "true")
public class TestJwtConfig {
    
    // 테스트용 시크릿 키 (실제 운영환경에서는 절대 사용하지 말 것!)
    private static final String TEST_SECRET = "test-secret-key-for-local-development-only-do-not-use-in-production";
    
    /**
     * 테스트용 Reactive JWT Decoder
     * HS256 알고리즘을 사용하여 JWT 토큰을 검증
     * 
     * @return ReactiveJwtDecoder 테스트용 JWT 디코더
     */
    @Bean
    @Primary
    public ReactiveJwtDecoder testReactiveJwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(
            TEST_SECRET.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        
        return NimbusReactiveJwtDecoder
                .withSecretKey(secretKey)
                .build();
    }
    
    /**
     * 일반 JWT Decoder (테스트용)
     * 
     * @return JwtDecoder 테스트용 JWT 디코더
     */
    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(
            TEST_SECRET.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        
        return NimbusJwtDecoder
                .withSecretKey(secretKey)
                .build();
    }
}