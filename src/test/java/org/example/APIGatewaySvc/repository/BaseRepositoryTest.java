package org.example.APIGatewaySvc.repository;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA Repository 레이어 테스트를 위한 기본 추상 클래스
 * JPA 및 데이터베이스 관련 기능만 로드하여 빠른 단위 테스트 수행
 * 
 * 주요 기능:
 * - @DataJpaTest를 통한 JPA 레이어만 로딩 (빠른 테스트)
 * - 내장형 H2 데이터베이스 사용
 * - TestEntityManager를 통한 엔티티 직접 조작 및 검증
 * - 트랜잭션 자동 롤백으로 테스트 간 데이터 격리
 */
@DataJpaTest
@ActiveProfiles("test")
public abstract class BaseRepositoryTest {

    /** JPA 엔티티 직접 조작을 위한 TestEntityManager */
    @Autowired
    protected TestEntityManager entityManager;
}