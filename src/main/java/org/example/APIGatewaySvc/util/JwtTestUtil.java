package org.example.APIGatewaySvc.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 토큰 테스트 유틸리티
 * 로컬 개발 및 테스트를 위한 JWT 토큰 생성 도구
 *
 * 주의: 이 클래스는 오직 테스트 목적으로만 사용되어야 하며,
 * 운영 환경에서는 실제 Auth0에서 발급받은 토큰을 사용해야 합니다.
 */
@Component
public class JwtTestUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTestUtil.class);

    private static final String TEST_SECRET = "test-secret-key-for-local-development-only-do-not-use-in-production";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    /**
     * 기본 테스트 JWT 토큰 생성
     *
     * @return 테스트용 JWT 토큰
     */
    public static String createTestToken() {
        return createTestToken("test-user-123", List.of("read:users", "write:users"));
    }

    /**
     * 사용자 ID와 권한을 지정한 테스트 JWT 토큰 생성
     *
     * @param userId 사용자 ID
     * @param permissions 권한 목록
     * @return 테스트용 JWT 토큰
     */
    public static String createTestToken(String userId, List<String> permissions) {
        Instant now = Instant.now();

        return Jwts.builder()
                .setSubject(userId)
                .setIssuer("https://api-bridge.us.auth0.com/")
                .setAudience("https://api.api-bridge.com")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .claim("permissions", permissions)
                .claim("scope", String.join(" ", permissions))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 만료된 테스트 JWT 토큰 생성 (에러 테스트용)
     *
     * @return 만료된 테스트용 JWT 토큰
     */
    public static String createExpiredToken() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        return Jwts.builder()
                .setSubject("test-user-expired")
                .setIssuer("https://api-bridge.us.auth0.com/")
                .setAudience("https://api.api-bridge.com")
                .setIssuedAt(Date.from(past.minus(1, ChronoUnit.HOURS)))
                .setExpiration(Date.from(past))
                .claim("permissions", List.of("read:users"))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 잘못된 오디언스를 가진 테스트 JWT 토큰 생성 (에러 테스트용)
     *
     * @return 잘못된 오디언스의 테스트용 JWT 토큰
     */
    public static String createInvalidAudienceToken() {
        Instant now = Instant.now();

        return Jwts.builder()
                .setSubject("test-user-invalid-aud")
                .setIssuer("https://api-bridge.us.auth0.com/")
                .setAudience("https://wrong-audience.com")  // 잘못된 오디언스
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .claim("permissions", List.of("read:users"))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 사전 정의된 테스트 토큰들을 반환하는 메서드
     *
     * @return 테스트 시나리오별 JWT 토큰 맵
     */
    public static Map<String, String> getTestTokens() {
        return Map.of(
            "valid", createTestToken(),
            "admin", createTestToken("admin-user", List.of("read:users", "write:users", "admin:all")),
            "readonly", createTestToken("readonly-user", List.of("read:users")),
            "expired", createExpiredToken(),
            "invalid_audience", createInvalidAudienceToken()
        );
    }

    /**
     * 토큰 정보를 출력하는 메서드 (디버깅용)
     *
     * @param token JWT 토큰
     */
    public static void printTokenInfo(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            logger.info("=== JWT Token Info ===");
            logger.info("Subject: {}", claims.getSubject());
            logger.info("Issuer: {}", claims.getIssuer());
            logger.info("Audience: {}", claims.getAudience());
            logger.info("Issued At: {}", claims.getIssuedAt());
            logger.info("Expires At: {}", claims.getExpiration());
            logger.info("Permissions: {}", claims.get("permissions"));
            logger.info("====================");
        } catch (JwtException e) {
            logger.error("토큰 파싱 실패: {}", e.getMessage());
        }
    }

    // Instance methods for test injection

    /**
     * 유효한 JWT 토큰 생성 (인스턴스 메서드)
     */
    public String generateValidToken(String email) {
        return createTestToken(email, List.of("read:users", "write:users"));
    }

    /**
     * 만료된 JWT 토큰 생성 (인스턴스 메서드)
     */
    public String generateExpiredToken(String email) {
        return createExpiredToken();
    }

    /**
     * 잘못된 오디언스를 가진 JWT 토큰 생성 (인스턴스 메서드)
     */
    public String generateTokenWithWrongAudience(String email) {
        return createInvalidAudienceToken();
    }
}