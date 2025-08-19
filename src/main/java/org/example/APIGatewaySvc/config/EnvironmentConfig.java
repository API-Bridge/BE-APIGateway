package org.example.APIGatewaySvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

/**
 * 환경변수 설정 및 검증을 위한 Configuration 클래스
 * - .env 파일의 환경변수를 Spring Boot에서 사용할 수 있도록 설정
 * - 실행 시 환경변수 주입 여부를 확인하는 기능 제공
 */
@Slf4j
@Configuration
@PropertySource(value = "file:.env", ignoreResourceNotFound = true)
public class EnvironmentConfig {

    private final Environment environment;

    // Auth0 관련 환경변수
    @Value("${AUTH0_CLIENT_ID:}")
    private String auth0ClientId;

    @Value("${AUTH0_CLIENT_SECRET:}")
    private String auth0ClientSecret;

    @Value("${AUTH0_ISSUER_URI:}")
    private String auth0IssuerUri;

    @Value("${AUTH0_AUDIENCE:}")
    private String auth0Audience;

    // Redis 관련 환경변수
    @Value("${SPRING_REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${SPRING_REDIS_PORT:6379}")
    private String redisPort;

    // Kafka 관련 환경변수
    @Value("${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}")
    private String kafkaBootstrapServers;

    // 서버 포트
    @Value("${SERVER_PORT:8080}")
    private String serverPort;

    public EnvironmentConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 애플리케이션 시작 시 환경변수 주입 상태를 확인하고 로그에 출력
     */
    @PostConstruct
    public void validateEnvironmentVariables() {
        log.info("=== 환경변수 주입 상태 확인 ===");
        
        // Auth0 관련 환경변수 검증
        validateAndLog("AUTH0_CLIENT_ID", auth0ClientId);
        validateAndLog("AUTH0_CLIENT_SECRET", auth0ClientSecret, true); // 민감정보는 마스킹
        validateAndLog("AUTH0_ISSUER_URI", auth0IssuerUri);
        validateAndLog("AUTH0_AUDIENCE", auth0Audience);
        
        // 기타 환경변수
        validateAndLog("SPRING_REDIS_HOST", redisHost);
        validateAndLog("SPRING_REDIS_PORT", redisPort);
        validateAndLog("KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrapServers);
        validateAndLog("SERVER_PORT", serverPort);
        
        log.info("=== 환경변수 검증 완료 ===");
        
        // Auth0 필수 환경변수 누락 시 경고
        if (isBlank(auth0ClientId) || isBlank(auth0ClientSecret) || 
            isBlank(auth0IssuerUri) || isBlank(auth0Audience)) {
            log.warn("⚠️ Auth0 필수 환경변수가 누락되었습니다. JWT 인증이 정상 작동하지 않을 수 있습니다.");
        }
    }

    /**
     * 환경변수 값을 검증하고 로그에 출력
     */
    private void validateAndLog(String varName, String value) {
        validateAndLog(varName, value, false);
    }

    private void validateAndLog(String varName, String value, boolean maskValue) {
        if (isBlank(value)) {
            log.warn("❌ {} : 설정되지 않음 (기본값 사용 또는 누락)", varName);
        } else {
            String displayValue = maskValue ? maskSensitiveValue(value) : value;
            log.info("✅ {} : {}", varName, displayValue);
        }
    }

    /**
     * 민감한 정보를 마스킹하여 출력
     */
    private String maskSensitiveValue(String value) {
        if (isBlank(value)) return "설정되지 않음";
        if (value.length() <= 4) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 문자열이 비어있는지 확인
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 런타임에 환경변수 상태를 확인할 수 있는 Bean
     */
    @Bean
    public EnvironmentChecker environmentChecker() {
        return new EnvironmentChecker(environment);
    }

    /**
     * 실행 중 환경변수 상태를 확인할 수 있는 헬퍼 클래스
     */
    public static class EnvironmentChecker {
        private final Environment environment;

        public EnvironmentChecker(Environment environment) {
            this.environment = environment;
        }

        /**
         * 특정 환경변수의 현재 값을 확인
         */
        public String getEnvironmentVariable(String name) {
            return environment.getProperty(name);
        }

        /**
         * 환경변수가 설정되어 있는지 확인
         */
        public boolean isEnvironmentVariableSet(String name) {
            String value = environment.getProperty(name);
            return value != null && !value.trim().isEmpty();
        }

        /**
         * 모든 Auth0 환경변수가 설정되어 있는지 확인
         */
        public boolean areAuth0VariablesSet() {
            return isEnvironmentVariableSet("AUTH0_CLIENT_ID") &&
                   isEnvironmentVariableSet("AUTH0_CLIENT_SECRET") &&
                   isEnvironmentVariableSet("AUTH0_ISSUER_URI") &&
                   isEnvironmentVariableSet("AUTH0_AUDIENCE");
        }
    }
}