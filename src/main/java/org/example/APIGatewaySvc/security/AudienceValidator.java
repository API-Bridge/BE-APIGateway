package org.example.APIGatewaySvc.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/**
 * Auth0 JWT 토큰의 Audience(오디언스) 검증을 위한 커스텀 Validator
 * 
 * Auth0에서 발급된 JWT 토큰이 올바른 API를 대상으로 하는지 검증합니다.
 * 이는 토큰이 다른 API나 애플리케이션에서 사용되는 것을 방지하는 보안 메커니즘입니다.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    /**
     * AudienceValidator 생성자
     * 
     * @param expectedAudience 검증할 대상 audience 값 (Auth0에서 설정한 API Identifier)
     */
    public AudienceValidator(String expectedAudience) {
        Assert.hasText(expectedAudience, "Expected audience cannot be null or empty");
        this.expectedAudience = expectedAudience;
    }

    /**
     * JWT 토큰의 audience 클레임을 검증합니다.
     * 
     * @param jwt 검증할 JWT 토큰
     * @return 검증 결과 (성공 또는 실패)
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // JWT의 audience 클레임 확인
        if (jwt.getAudience() != null && jwt.getAudience().contains(this.expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }

        // Audience 검증 실패 시 에러 반환
        OAuth2Error error = new OAuth2Error(
            "invalid_audience",
            "The required audience is missing or invalid. Expected: " + this.expectedAudience,
            null
        );
        
        return OAuth2TokenValidatorResult.failure(error);
    }
}