package org.example.APIGatewaySvc.unit;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

/**
 * 단위 테스트를 위한 기본 추상 클래스
 * Spring Context 로딩 없이 Mockito를 사용한 빠른 단위 테스트 수행
 * 
 * 주요 기능:
 * - Mockito Extension을 통한 목 객체 자동 생성 및 주입
 * - Spring Context 로딩 없이 가장 빠른 테스트 수행
 * - 비즈니스 로직 단위 테스트에 적합
 * - @Mock, @InjectMocks 등 Mockito 애노테이션 자동 처리
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public abstract class BaseUnitTest {
}