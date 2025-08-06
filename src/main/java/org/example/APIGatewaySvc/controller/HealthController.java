package org.example.APIGatewaySvc.controller;

import org.example.APIGatewaySvc.dto.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 서비스 상태 확인용 Health Check 컨트롤러
 * MSA 환경에서 서비스 가용성 모니터링 및 로드밸런싱을 위한 엔드포인트 제공
 * 
 * 주요 기능:
 * - 서비스 상태 확인 (UP/DOWN)
 * - 애플리케이션 버전 정보 제공
 * - 시스템 정보 제공 (JVM 버전, 시간 등)
 * - Kubernetes 사이드카 서비스나 로드밸런서에서 사용
 */
@Tag(name = "Health Check", description = "서비스 상태 확인 API")
@RestController
@RequestMapping("/health")
public class HealthController {

    /** 애플리케이션 서비스명 */
    @Value("${spring.application.name}")
    private String serviceName;

    /** 빌드 버전 (기본값: dev) */
    @Value("${BUILD_VERSION:dev}")
    private String version;

    /**
     * 서비스 상태 확인 엔드포인트
     * 마이크로서비스의 현재 상태와 시스템 정보를 반환
     * 
     * @return BaseResponse<Map<String, Object>> 서비스 상태 정보
     */
    @Operation(summary = "서비스 상태 확인", description = "서비스의 현재 상태를 확인합니다.")
    @GetMapping
    public BaseResponse<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        
        // 서비스 기본 정보
        status.put("service", serviceName);
        status.put("status", "UP");
        status.put("version", version);
        status.put("timestamp", LocalDateTime.now());
        
        // JVM 시스템 정보
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("javaVendor", System.getProperty("java.vendor"));
        
        return BaseResponse.success(status, "Service is healthy");
    }
}