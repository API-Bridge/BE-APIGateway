package org.example.APIGatewaySvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Custom API Service의 메인 애플리케이션 클래스
 * MSA 아키텍처에서 API 브리지 역할을 수행하는 Spring Boot 애플리케이션의 진입점
 * 
 * 주요 기능:
 * - Spring Boot 자동 구성 활성화
 * - 애플리케이션 시작 시 필요한 빈들을 스캔하고 초기화
 * - 서블릿 컨테이너 내장 및 HTTP 서버 구동
 */
@SpringBootApplication
public class APIGatewaySvcApplication {

    /**
     * 애플리케이션의 메인 진입점
     * Spring Boot 애플리케이션을 시작하고 필요한 모든 구성 요소를 초기화
     * 
     * @param args 명령행 인수
     */
    public static void main(String[] args) {
        SpringApplication.run(APIGatewaySvcApplication.class, args);
    }

}
