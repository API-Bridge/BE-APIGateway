package org.example.APIGatewaySvc.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JWT 토큰에서 사용자 정보 및 권한을 추출하는 유틸리티 클래스
 * Spring Security Context에서 JWT 토큰 정보를 안전하게 추출하고 처리
 * 
 * 주요 기능:
 * - JWT 토큰에서 사용자 ID, 이메일, 이름 등 클레임 추출
 * - 사용자 권한(Permission) 확인 및 검증
 * - 다중 권한 확인 지원
 * - 안전한 Optional 기반 값 반환
 */
@Slf4j
@Component
public class JwtUtils {

    public Optional<String> getCurrentUserId() {
        return getClaimFromJwt("sub");
    }

    public Optional<String> getCurrentUserEmail() {
        return getClaimFromJwt("email");
    }

    public Optional<String> getCurrentUserName() {
        return getClaimFromJwt("name");
    }

    public Optional<String> getClaimFromJwt(String claim) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                return Optional.ofNullable(jwt.getClaimAsString(claim));
            }
        } catch (Exception e) {
            log.debug("Failed to extract claim '{}' from JWT: {}", claim, e.getMessage());
        }

        return Optional.empty();
    }

    public boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication != null &&
               authentication.getAuthorities().stream()
                   .anyMatch(auth -> auth.getAuthority().equals(permission));
    }

    public boolean hasAnyPermission(String... permissions) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        for (String permission : permissions) {
            if (authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(permission))) {
                return true;
            }
        }

        return false;
    }
}