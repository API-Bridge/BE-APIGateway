package org.example.APIGatewaySvc.integration;

import org.example.APIGatewaySvc.testcontainers.TestContainerConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트를 위한 기본 추상 클래스
 * 전체 애플리케이션 컴텍스트를 로드하여 실제 환경과 유사한 테스트 수행
 * 
 * 주요 기능:
 * - TestContainers를 사용한 실제 DB, Redis, Kafka 환경 구성
 * - 전체 Spring Boot 애플리케이션 컴텍스트 로딩
 * - 랜덤 포트로 웹 서버 구동 및 HTTP API 테스트
 * - 트랜잭션 롤백 지원으로 테스트 간 데이터 격리
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.kafka=DEBUG"
})
@ContextConfiguration(classes = TestContainerConfig.class)
@Testcontainers
@Transactional
public abstract class BaseIntegrationTest {
}