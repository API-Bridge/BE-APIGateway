package org.example.APIGatewaySvc.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Collections;

/**
 * API Gateway용 JWT 역할 유틸리티
 *
 * Spring Cloud Gateway의 WebFlux 환경에서 JWT 토큰의 Auth0 역할을 추출합니다.
 */
@Component
@Slf4j
public class JwtRoleUtils {

    // Auth0에서 설정한 커스텀 클레임 네임스페이스
    private static final String PERMISSIONS_CLAIM = "https://api-bridge.com/permissions";


    /**
     * JWT 토큰에서 Auth0 권한 목록을 추출합니다.
     *
     * @param jwt JWT 토큰
     * @return 사용자 권한 목록
     */
    public List<String> extractPermissions(Jwt jwt) {
        try {
            if (jwt == null) {
                log.debug("JWT 토큰이 null입니다");
                return Collections.emptyList();
            }

            List<String> permissions = jwt.getClaimAsStringList(PERMISSIONS_CLAIM);

            if (permissions != null && !permissions.isEmpty()) {
                log.debug("JWT에서 추출한 권한: {}", permissions);
                return permissions;
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.error("JWT 토큰에서 권한 추출 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }



    /**
     * 사용자가 특정 권한을 가지고 있는지 확인합니다.
     *
     * @param jwt JWT 토큰
     * @param permission 확인할 권한
     * @return 권한을 가지고 있으면 true
     */
    public boolean hasPermission(Jwt jwt, String permission) {
        List<String> permissions = extractPermissions(jwt);
        boolean hasPermission = permissions.contains(permission);
        log.debug("권한 '{}' 확인 결과: {}, 사용자 권한: {}", permission, hasPermission, permissions);
        return hasPermission;
    }

    /**
     * Authentication에서 JWT를 추출합니다.
     *
     * @param authentication Spring Security Authentication 객체
     * @return JWT 토큰 또는 null
     */
    public Jwt extractJwtFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            log.debug("Authentication 객체가 null입니다");
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }

        log.debug("Authentication에 JWT 토큰이 없습니다. Principal type: {}",
            authentication.getPrincipal().getClass().getSimpleName());
        return null;
    }


    /**
     * 사용자가 여러 Auth0 권한 중 하나라도 가지고 있는지 확인합니다.
     *
     * @param jwt JWT 토큰
     * @param permissions 확인할 권한들
     * @return 하나라도 가지고 있으면 true
     */
    public boolean hasAnyPermission(Jwt jwt, String... permissions) {
        List<String> userPermissions = extractPermissions(jwt);

        for (String permission : permissions) {
            if (userPermissions.contains(permission)) {
                log.debug("권한 중 '{}' 발견", permission);
                return true;
            }
        }

        log.debug("요청한 권한들 중 어느 것도 없음: {}, 사용자 권한: {}", List.of(permissions), userPermissions);
        return false;
    }

    /**
     * JWT 토큰에서 사용자 ID(subject)를 추출합니다.
     *
     * @param jwt JWT 토큰
     * @return Auth0 사용자 ID
     */
    public String extractAuth0Id(Jwt jwt) {
        try {
            if (jwt == null) {
                return null;
            }
            return jwt.getSubject();
        } catch (Exception e) {
            log.error("JWT 토큰에서 Auth0 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JWT 토큰에서 사용자 이메일을 추출합니다.
     *
     * @param jwt JWT 토큰
     * @return 사용자 이메일
     */
    public String extractEmail(Jwt jwt) {
        try {
            if (jwt == null) {
                return null;
            }
            return jwt.getClaimAsString("email");
        } catch (Exception e) {
            log.error("JWT 토큰에서 이메일 추출 실패: {}", e.getMessage());
            return null;
        }
    }
}