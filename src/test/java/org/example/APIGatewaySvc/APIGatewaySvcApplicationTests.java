package org.example.APIGatewaySvc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Custom API Service 애플리케이션 기본 테스트 클래스
 * Spring Boot 애플리케이션 컴텍스트 로딩 및 기본 기능 테스트
 * 
 * 주요 기능:
 * - Spring Boot 애플리케이션 컴텍스트 로딩 확인
 * - 기본 빈 구성 및 의존성 주입 확인
 * - 애플리케이션 시작 가능 여부 검증
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.cloud.gateway.config.GatewayAutoConfiguration," +
            "org.springframework.security.oauth2.server.resource.web.reactive.function.server.ServerOAuth2AuthorizedClientExchangeFilterFunction"
    }
)
@Disabled("Context loading test disabled - requires external dependencies")
class APIGatewaySvcApplicationTests {

    /**
     * Spring Boot 애플리케이션 컴텍스트 로딩 테스트
     * 기본 구성요소만 로드하여 컨텍스트 로딩 확인
     */
    @Test  
    void contextLoads() {
        // 기본 컴텍스트가 정상적으로 로드되는지 확인
        // 복잡한 의존성 없이 기본 Spring Boot 자동 구성만 검증
    }

}
