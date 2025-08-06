package org.example.APIGatewaySvc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 레이어 테스트를 위한 기본 추상 클래스
 * Spring Boot의 @WebMvcTest를 사용하여 웹 레이어만 로드하여 빠른 테스트 수행
 * 
 * 주요 기능:
 * - MockMvc를 통한 HTTP 요청/응답 테스트
 * - JSON 직렬화/역직렬화 지원
 * - 컨트롤러 레이어 단위 테스트 환경
 * - Test 프로파일 사용으로 분리된 설정
 */
@WebMvcTest
@ActiveProfiles("test")
public abstract class BaseControllerTest {

    /** HTTP 요청 시뮤레이션을 위한 MockMvc */
    @Autowired
    protected MockMvc mockMvc;

    /** JSON 직렬화/역직렬화를 위한 ObjectMapper */
    @Autowired
    protected ObjectMapper objectMapper;
}