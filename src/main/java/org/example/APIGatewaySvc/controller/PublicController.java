package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.net.URI;

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
@Tag(name = "Public Controller", description = "공개 API - 인증 없이 접근 가능한 엔드포인트")
public class PublicController {

    @Value("${auth0.issuerUri}")
    private String issuerUri;

    @Value("${auth0.client-id:}")
    private String clientId;

    @Value("${auth0.postLogoutUri:http://localhost:8080/public/auth-test.html}")
    private String defaultPostLogoutUri;

    /**
     * API Gateway 상태 확인 엔드포인트
     * 외부 모니터링 도구나 로드밸런서의 헬스체크용
     * 
     * @return Mono<ResponseEntity<Map<String, Object>>> Gateway 상태 정보
     */
    @GetMapping("/health")
    @Operation(
        summary = "Public Health Check", 
        description = """
            **공개 헬스체크 엔드포인트**
            
            **인증**: 불필요 (완전 공개)
            
            **목적**:
            - 외부 모니터링 도구의 헬스체크
            - 로드밸런서의 인스턴스 상태 확인
            - 서비스 가용성 모니터링
            
            **응답 정보**:
            - service: 서비스 이름
            - status: 서비스 상태 (UP/DOWN)
            - timestamp: 응답 생성 시간
            - version: 서비스 버전
            
            **활용 예시**:
            - Kubernetes liveness/readiness probe
            - AWS Application Load Balancer health check
            - Prometheus 모니터링
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Gateway가 정상적으로 실행중입니다")
    })
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


    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}