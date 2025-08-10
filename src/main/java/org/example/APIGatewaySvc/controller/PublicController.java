package org.example.APIGatewaySvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 공개 접근 가능한 엔드포인트 컨트롤러
 * 인증이 필요하지 않은 공개 API 제공
 * 
 * 주요 기능:
 * - Gateway 상태 확인 엔드포인트
 * - 인증 불필요한 정보 제공
 * - 테스트 및 모니터링 지원
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    /**
     * API Gateway 상태 확인 엔드포인트
     * 외부 모니터링 도구나 로드밸런서의 헬스체크용
     * 
     * @return Mono<ResponseEntity<Map<String, Object>>> Gateway 상태 정보
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "api-gateway");
        status.put("status", "UP");
        status.put("timestamp", Instant.now().toString());
        status.put("version", "1.0.0");
        
        return Mono.just(ResponseEntity.ok(status));
    }

    /**
     * API Gateway 정보 조회 엔드포인트
     * 공개적으로 접근 가능한 메타데이터 제공
     * 
     * @return Mono<ResponseEntity<Map<String, Object>>> Gateway 정보
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "API Gateway Service");
        info.put("description", "Spring Cloud Gateway with Auth0 JWT Authentication");
        info.put("version", "1.0.0");
        info.put("documentation", "/swagger-ui.html");
        
        return Mono.just(ResponseEntity.ok(info));
    }
}