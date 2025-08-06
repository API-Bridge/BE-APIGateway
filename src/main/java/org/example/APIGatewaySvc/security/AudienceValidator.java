package org.example.APIGatewaySvc.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Auth0 JWT 토큰의 Audience 클레임 검증자
 * JWT 토큰이 올바른 API(대상 어디언스)를 위해 발급되었는지 확인
 * 
 * 주요 기능:
 * - JWT 토큰의 'aud'(어디언스) 클레임 검증
 * - 토큰 오남용 및 보안 공격 방지
 * - Auth0에서 설정한 API Identifier와 일치 여부 확인
 * - Spring Security OAuth2 Token Validator 인터페이스 구현
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2Error error = new OAuth2Error("invalid_audience", "The required audience is missing", null);

        if (jwt.getAudience() != null && jwt.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(error);
    }
}