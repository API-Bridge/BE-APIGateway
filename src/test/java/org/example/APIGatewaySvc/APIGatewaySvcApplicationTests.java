package org.example.APIGatewaySvc;

import org.junit.jupiter.api.Test;
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
@SpringBootTest
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class APIGatewaySvcApplicationTests {

    /**
     * Spring Boot 애플리케이션 컴텍스트 로딩 테스트
     * 모든 빈이 정상적으로 생성되고 의존성 주입이 올바르게 수행되는지 확인
     */
    @Test
    void contextLoads() {
        // 컴텍스트가 정상적으로 로드되는지 확인
        // Spring Boot 자동 구성이 올바르게 작동하는지 검증
    }

}
