package org.example.APIGatewaySvc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 (Swagger) 문서화 설정 클래스
 * API 문서 자동 생성 및 Swagger UI 커스터마이징을 위한 설정
 * 
 * 주요 기능:
 * - API 문서 메타데이터 설정 (제목, 버전, 설명, 연락처 등)
 * - 다중 환경 서버 설정 (로컬, 개발, 운영)
 * - JWT Bearer 토큰 인증 스키마 설정
 * - OpenAPI 스펙 기반 자동 문서 생성
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("API Gateway")
                .version("1.0.0")
                .description("""
                    # 🚀 API Bridge MSA Gateway Service
                    
                    마이크로서비스 아키텍처를 위한 통합 API Gateway로, 인증, 라우팅, 보안, 모니터링 기능을 제공합니다.
                    
                    ## 🔒 인증 및 보안 시스템
                    
                    ### JWT 인증
                    - **Auth0 JWT 토큰 검증**: JWKS 엔드포인트를 통한 실시간 서명 검증
                    - **Audience 검증**: 토큰 오남용 방지를 위한 audience 클레임 검증
                    - **자동 로그인 실패 감지**: JWT 인증 실패 시 Redis 기반 자동 추적 및 차단
                    
                    ### 자동 차단 시스템 (Redis 기반)
                    - **사용자별 차단**: 5회 로그인 실패 시 30분 자동 차단
                    - **IP별 차단**: 10회 로그인 실패 시 30분 자동 차단  
                    - **실시간 감지**: LoginAttemptTrackingFilter가 401 응답을 모니터링하여 자동 처리
                    - **캐시 최적화**: 15분 윈도우로 실패 횟수 캐싱, 성공 시 자동 리셋
                    
                    ### Rate Limiting (Redis 기반)
                    - **사용자별 제한**: JWT sub 클레임 기반 개별 사용자 Rate Limit
                    - **IP별 제한**: 익명 사용자에 대한 IP 기반 제한
                    - **서비스별 정책**: 마이크로서비스별 차별화된 Rate Limit 적용
                    
                    ## 🏗️ 마이크로서비스 라우팅 & 서킷브레이커
                    
                    ### 라우팅 경로 및 정책
                    | 서비스 | 경로 | Rate Limit | Circuit Breaker | 설명 |
                    |--------|------|------------|-----------------|------|
                    | **User Service** | `/gateway/users/**` | 20 req/s | userSvcCb | 사용자 관리 (관대한 정책) |
                    | **API Management** | `/gateway/apimgmt/**` | 15 req/s | apiMgmtSvcCb | API 관리 서비스 |
                    | **Custom API Mgmt** | `/gateway/customapi/**` | 15 req/s | customApiMgmtSvcCb | 커스텀 API 관리 |
                    | **AI Feature** | `/gateway/aifeature/**` | 5 req/s (2토큰) | aiFeatureSvcCb | AI 기능 (엄격한 정책) |
                    | **System Management** | `/gateway/sysmgmt/**` | 15 req/s | systemMgmtSvcCb | 시스템 관리 |
                    
                    ### 서킷브레이커 설정 (Resilience4j)
                    ```yaml
                    - 슬라이딩 윈도우: 20개 요청
                    - 최소 호출 수: 10개 (평가 기준)
                    - 실패율 임계값: 50% (OPEN 상태 전환)
                    - OPEN 상태 대기: 10초 (자동 HALF_OPEN 전환)
                    - 느린 호출 임계값: 3초 (AI 서비스는 5초)
                    ```
                    
                    ### Fallback 처리
                    - **RFC 7807 표준**: Problem Details 형식의 에러 응답
                    - **서비스별 메시지**: 각 마이크로서비스에 맞는 사용자 친화적 메시지
                    - **추적성**: Request ID를 통한 분산 추적 지원
                    
                    ## 🧪 목업 서비스 (로컬 테스트용)
                    
                    실제 마이크로서비스 개발 전까지 테스트를 위한 Mock API 제공:
                    
                    ### 목업 엔드포인트
                    - **사용자 프로필**: `GET /mock/users/profile` - JWT에서 사용자 정보 추출하여 반환
                    - **API 목록**: `GET /mock/apimgmt/apis` - API 관리 서비스 시뮬레이션
                    - **AI 채팅**: `POST /mock/aifeature/chat` - AI 대화 시뮬레이션 (1-3초 지연)
                    - **시스템 설정**: `GET /mock/sysmgmt/config` - 시스템 설정 조회
                    
                    ### 테스트 시나리오
                    - **에러 시뮬레이션**: `/mock/users/error/{statusCode}` - 다양한 HTTP 에러 생성
                    - **느린 응답**: `/mock/users/slow` - 서킷브레이커 테스트용 3-8초 지연
                    - **랜덤 실패**: `/mock/users/random-fail` - 50% 확률 실패로 서킷브레이커 동작 테스트
                    
                    ## 🛡️ 관리자 기능 (내부 API)
                    
                    ### 차단 관리 (`/internal/block/**`)
                    | 타입 | 설명 | 키 형식 | 예시 |
                    |------|------|---------|------|
                    | **user** | 사용자 ID 차단 | `blocked:user:{userId}` | JWT sub claim |
                    | **ip** | IP 주소 차단 | `blocked:ip:{ipAddress}` | X-Forwarded-For 우선 |
                    | **key** | API 키 차단 | `blocked:key:{apiKey}` | X-Api-Key 헤더 |
                    
                    ### 로그인 시도 모니터링 (`/internal/login-attempts/**`)
                    - **실시간 통계**: 현재/남은 시도 횟수, 윈도우 만료시간
                    - **캐시 기반**: Redis 15분 윈도우 캐싱으로 빠른 응답
                    - **자동 초기화**: 성공 로그인 시 실패 횟수 자동 리셋
                    - **예방 기능**: 차단 임박 시 경고 가능
                    
                    ## 📊 모니터링 및 추적
                    
                    ### 표준 응답 포맷
                    모든 응답이 일관된 JSON 형식으로 표준화됩니다:
                    ```json
                    {
                      "success": true/false,
                      "code": "SUCCESS|ERROR_CODE",
                      "message": "사용자 친화적 메시지",
                      "data": { /* 실제 데이터 */ },
                      "meta": {
                        "requestId": "UUID",
                        "durationMs": 125,
                        "gateway": "API-Gateway"
                      }
                    }
                    ```
                    
                    ### Actuator 엔드포인트
                    - **헬스 체크**: `/actuator/health` - 전체 시스템 상태
                    - **서킷브레이커**: `/actuator/circuitbreakers` - CB 상태 실시간 조회
                    - **게이트웨이 라우트**: `/actuator/gateway/routes` - 라우팅 설정 확인
                    - **Prometheus**: `/actuator/prometheus` - 메트릭 수집
                    
                    ## 📝 사용 방법
                    
                    ### 1. 인증 토큰 획득
                    ```bash
                    # Auth0 로그인 (브라우저)
                    http://localhost:8080/auth/login
                    
                    # 또는 테스트 페이지 이용
                    http://localhost:8080/public/auth-test.html
                    ```
                    
                    ### 2. API 호출
                    ```bash
                    curl -H "Authorization: Bearer {JWT_TOKEN}" \\
                         http://localhost:8080/gateway/users/profile
                    ```
                    
                    ### 3. Rate Limit 확인
                    응답 헤더에서 제한 정보 확인:
                    ```
                    X-RateLimit-Limit: 20
                    X-RateLimit-Remaining: 18  
                    X-RateLimit-Reset: 1674645600
                    ```
                    
                    ## ⚠️ 운영 환경 주의사항
                    
                    - **로컬 환경**: 목업 서비스 사용, 테스트 JWT 토큰 허용
                    - **운영 환경**: 실제 마이크로서비스 연동, Auth0 JWT만 허용
                    - **보안**: 민감정보 자동 마스킹, Request ID 기반 추적
                    - **성능**: WebFlux 비동기 처리, Redis 연결 풀 최적화
                    
                    🎯 **목표**: 안전하고 확장 가능한 마이크로서비스 API Gateway 제공
                    """)
                .contact(new Contact()
                    .name("API Bridge Team")
                    .email("support@api-bridge.com")
                    .url("https://api-bridge.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                new Server().url("https://gateway.api-bridge.dev").description("개발 환경"),
                new Server().url("https://gateway.api-bridge.com").description("운영 환경")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT Authorization header using the Bearer scheme.")));
    }
}