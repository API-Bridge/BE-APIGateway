package org.example.APIGatewaySvc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA(Java Persistence API) 설정 클래스
 * 데이터베이스 연결 및 ORM 설정을 관리
 * 
 * 주요 기능:
 * - JPA Repository 스캔 및 활성화
 * - JPA Auditing 활성화 (생성일자, 수정일자 자동 설정)
 * - 엔티티 매니저 및 트랜잭션 관리 설정
 */
@Configuration
@EnableJpaRepositories(basePackages = "org.example.APIGatewaySvc.repository")
@EnableJpaAuditing
public class JpaConfig {
}