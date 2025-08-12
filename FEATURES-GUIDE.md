# API Gateway 기능 가이드

이 문서는 API Gateway에 구현된 주요 기능들에 대한 종합 가이드입니다.

---

## 📋 목차

1. [사용자/IP 차단 기능](#1-사용자ip-차단-기능)
2. [서킷브레이커 및 RateLimit 헤더](#2-서킷브레이커-및-ratelimit-헤더)
3. [표준 응답 필터 (StandardResponseFilter)](#3-표준-응답-필터-standardresponsefilter)
4. [Auth0 JWT 인증 테스트](#4-auth0-jwt-인증-테스트)

---

## 1. 사용자/IP 차단 기능

### 📝 개요

API Gateway 서비스에서 Redis 기반으로 특정 사용자 ID, IP 주소, API 키를 차단하는 보안 기능입니다. 로그인 실패, 비정상적인 접근 패턴, 악성 행위 등을 방지하기 위해 사용됩니다.

### 🔧 주요 기능

#### 1. 실시간 차단 검사
- 모든 요청에 대해 사전 필터링
- IP, 사용자 ID, API 키 동시 검사
- 병렬 처리로 성능 최적화
- X-Forwarded-For, X-Real-IP 헤더 지원

#### 2. 자동 로그인 실패 모니터링
- **실시간 추적**: JWT 인증 실패 자동 감지
- **사용자별 차단**: 5회 실패 시 30분 자동 차단
- **IP별 차단**: 10회 실패 시 30분 자동 차단
- **Redis 캐싱**: 15분 윈도우로 실패 횟수 캐싱
- **자동 초기화**: 성공 시 실패 횟수 자동 리셋

#### 3. 유연한 차단 관리
- **임시 차단**: TTL 기반 자동 해제
- **영구 차단**: 수동 해제 필요
- **차단 이유**: 상세한 차단 사유 기록
- **실시간 관리**: 즉시 차단/해제 가능

### 🗂️ Redis 데이터 구조

#### 키 패턴
```
# 차단 정보
blocked:user:{userId}     → 사용자 차단
blocked:ip:{ipAddress}    → IP 주소 차단  
blocked:key:{apiKey}      → API 키 차단

# 로그인 시도 캐싱
login_attempts:{userId}   → 사용자별 로그인 실패 횟수
login_attempts:ip:{ip}    → IP별 로그인 실패 횟수
```

#### 값 구조
```
# 차단 정보
키: blocked:user:user123
값: "Multiple failed login attempts"
TTL: 3600 (1시간 후 자동 해제)

# 로그인 시도 캐싱
키: login_attempts:user123
값: "3" (현재 실패 횟수)
TTL: 900 (15분 윈도우)
```

### 🌐 API 엔드포인트

#### 1. 차단 추가
```http
POST /internal/block/{type}?id={id}&ttlSeconds={ttl}&reason={reason}
```

**파라미터:**
- `type`: user, ip, key 중 하나
- `id`: 차단할 대상 ID
- `ttlSeconds`: TTL 초 단위 (선택사항, 미지정시 영구차단)
- `reason`: 차단 사유 (선택사항)

**예시:**
```bash
# 사용자 1시간 임시 차단
curl -X POST "localhost:8080/internal/block/user?id=user123&ttlSeconds=3600&reason=Failed login attempts"

# IP 영구 차단
curl -X POST "localhost:8080/internal/block/ip?id=192.168.1.100&reason=Malicious activity"
```

#### 2. 차단 해제
```http
DELETE /internal/block/{type}/{id}
```

#### 3. 차단 상태 확인
```http
GET /internal/block/{type}/{id}
```

#### 4. 로그인 시도 통계 조회
```http
GET /internal/login-attempts/user/{userId}
GET /internal/login-attempts/ip/{ipAddress}
```

### 🧪 테스트 방법

#### 단위 테스트 실행
```bash
./gradlew test --tests "*BlockCheckFilterTest"
./gradlew test --tests "*InternalBlockControllerTest"
```

#### 통합 테스트 실행
```bash
./gradlew test --tests "*BlockFeatureIntegrationTest"
./gradlew test --tests "*LoginAttemptIntegrationTest"
```

---

## 2. 서킷브레이커 및 RateLimit 헤더

### 🎯 개요

API Gateway에 서킷브레이커와 RateLimit 헤더 기능을 구현하여 다운스트림 서비스의 장애나 지연에 대한 빠른 실패 처리와 클라이언트에게 Rate Limiting 정보를 제공합니다.

### 🏗️ 아키텍처 설계

#### 1. 서킷브레이커 (Circuit Breaker)

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client        │───▶│   API Gateway   │───▶│  Downstream     │
│                 │    │                 │    │   Services      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │ Circuit Breaker │
                       │   States:       │
                       │  • CLOSED       │
                       │  • OPEN         │
                       │  • HALF_OPEN    │
                       └─────────────────┘
```

#### 2. RateLimit 헤더 시스템

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client        │───▶│ RateLimitHeaders│───▶│   Response      │
│                 │    │     Filter      │    │   + Headers     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │ Redis Rate      │
                       │ Limiter State   │
                       │ • tokens        │
                       │ • timestamp     │
                       └─────────────────┘
```

### ⚙️ 구현 상세

#### Resilience4j 서킷브레이커 설정
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 20              # 슬라이딩 윈도우 크기
        minimumNumberOfCalls: 10           # 최소 호출 수
        failureRateThreshold: 50           # 실패율 임계값 (50%)
        waitDurationInOpenState: 10s       # OPEN 상태 대기 시간
        slowCallDurationThreshold: 3s      # 느린 호출 임계값
        slowCallRateThreshold: 50          # 느린 호출 비율 임계값
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

#### RateLimit 헤더 구현
추가되는 헤더:
- **X-RateLimit-Limit**: 허용되는 총 요청 수
- **X-RateLimit-Remaining**: 남은 요청 수
- **X-RateLimit-Reset**: Rate Limit 재설정 시간 (Unix timestamp)

#### Fallback 처리 (RFC 7807)
```json
{
  "type": "about:blank",
  "title": "Service Unavailable",
  "status": 503,
  "detail": "User management service is temporarily unavailable. Login and profile features may not work.",
  "instance": "/gateway/users/profile"
}
```

### 📊 메트릭 및 모니터링

#### Prometheus 메트릭
- `resilience4j_circuitbreaker_state{name, state}` - 서킷브레이커 상태
- `resilience4j_circuitbreaker_calls_total{name, kind}` - 호출 수 (성공/실패)
- `spring_cloud_gateway_requests_total` - 총 요청 수

#### Health Check 엔드포인트
```bash
GET /actuator/circuitbreakers    # 서킷브레이커 상태 확인
GET /actuator/health             # 전체 Health 상태
GET /actuator/prometheus         # Prometheus 메트릭
```

### 🔒 보안 고려사항

#### 정보 노출 최소화
- 헤더 정보 제한: 필요한 최소한의 정보만 노출
- 내부 상태 숨김: Redis 키 구조나 내부 로직 노출 방지
- 사용자별 분리: 다른 사용자의 Rate Limit 정보 접근 차단

#### DoS 공격 방어
- 빠른 실패: 다운스트림 과부하 방지
- 리소스 보호: 불필요한 연결 시도 차단
- 연쇄 실패 방지: 서비스 간 장애 전파 차단

---

## 3. 표준 응답 필터 (StandardResponseFilter)

### 📝 개요

StandardResponseFilter는 Spring Cloud Gateway에서 모든 마이크로서비스의 응답을 일관된 형식으로 표준화하는 필터입니다.

### 🏗️ 아키텍처 설계

#### 응답 래핑 구조
```json
{
  "success": true|false,
  "code": "SUCCESS|ERROR_CODE",
  "message": "사용자 친화적 메시지",
  "data": { /* 성공 시 실제 데이터 */ },
  "error": { /* 실패 시 에러 상세 정보 */ },
  "meta": {
    "requestId": "uuid",
    "durationMs": 125,
    "gateway": "API-Gateway",
    "version": "1.0"
  },
  "timestamp": "2024-01-01T12:00:00.000Z"
}
```

#### 구현된 컴포넌트
```
src/main/java/org/example/APIGatewaySvc/
├── dto/
│   ├── StandardResponse.java      # 표준 응답 포맷 DTO
│   └── ErrorDetails.java          # 에러 상세 정보 DTO
├── filter/
│   └── StandardResponseFilter.java # 응답 래핑 필터
└── util/
    └── ErrorCodeMapper.java       # HTTP → 비즈니스 코드 매핑
```

### 🔧 설정 방법

#### Gateway 설정 (application.yml)
```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: StandardResponseFilter  # 추가된 부분
      routes:
        - id: user-service
          uri: http://localhost:8081
          predicates:
            - Path=/gateway/users/**
```

#### 적용 범위

**✅ 래핑 적용 경로**
- `/gateway/users/**` - 사용자 서비스
- `/gateway/apimgmt/**` - API 관리 서비스
- `/gateway/customapi/**` - 커스텀 API 서비스
- `/gateway/aifeature/**` - AI 기능 서비스
- `/gateway/sysmgmt/**` - 시스템 관리 서비스

**❌ 래핑 제외 경로**
- `/auth/**` - 인증 관련 엔드포인트
- `/public/**` - 공개 엔드포인트
- `/actuator/**` - 모니터링 엔드포인트

### 📊 에러 코드 매핑

| HTTP Status | Business Code | Error Type | 메시지 |
|-------------|---------------|------------|--------|
| 200-299 | SUCCESS | - | 요청이 성공적으로 처리되었습니다 |
| 401 | UNAUTHORIZED | AUTHENTICATION | 인증이 필요합니다 |
| 403 | FORBIDDEN | AUTHORIZATION | 접근 권한이 없습니다 |
| 404 | NOT_FOUND | CLIENT_ERROR | 요청한 리소스를 찾을 수 없습니다 |
| 429 | RATE_LIMITED | POLICY_VIOLATION | 요청 한도를 초과했습니다 |
| 500-503 | UPSTREAM_ERROR | INFRASTRUCTURE | 서버에서 오류가 발생했습니다 |

### 🧪 테스트 방법

#### 성공 응답 테스트
```bash
curl -X GET "http://localhost:8080/gateway/users/anything" \
  -H "Content-Type: application/json"
```

#### 에러 응답 테스트
```bash
curl -X GET "http://localhost:8080/gateway/users/status/404" \
  -H "Content-Type: application/json"
```

#### 단위 테스트 실행
```bash
./gradlew.bat test --tests "*StandardResponseFilterTest*"
```

### 🔒 보안 고려사항

#### 정보 누출 방지
- 민감한 정보 마스킹
- 내부 시스템 정보 숨김
- Request ID를 통한 추적성 제공

#### 성능 기반 보안
- 응답 크기 제한으로 메모리 고갈 방지
- 처리 시간 모니터링으로 비정상적 요청 탐지
- Circuit Breaker와 연계하여 장애 전파 방지

---

## 4. Auth0 JWT 인증 테스트

### 🎯 개요

API Gateway의 Auth0 JWT 토큰 인증을 테스트하는 방법을 설명합니다.

### 🚀 빠른 시작

#### 서버 시작
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

#### 테스트 페이지 접속
브라우저에서 http://localhost:8080/public/auth-test.html 접속

### 📱 브라우저 테스트

#### Step 1: Auth0 로그인
1. http://localhost:8080/public/auth-test.html 접속
2. "Auth0 로그인" 버튼 클릭
3. Auth0 로그인 페이지에서 인증 완료
4. 리다이렉트 후 JWT 토큰 자동 파싱

#### Step 2: 토큰 정보 확인
- JWT 헤더, 페이로드, 서명 정보 자동 표시
- 사용자 정보 (이메일, 이름 등) 표시
- 토큰 만료 시간 확인

#### Step 3: API 테스트
- "API 테스트" 버튼으로 인증 API 자동 호출
- 성공/실패 결과 실시간 확인

### 🔧 Postman 테스트

#### 인증 설정
1. **Authorization Type**: Bearer Token
2. **Token**: `{위에서 복사한 JWT 토큰}`

#### 테스트 엔드포인트

**✅ 공개 엔드포인트 (인증 불필요)**
```
GET http://localhost:8080/public/health
GET http://localhost:8080/public/auth0-config
GET http://localhost:8080/actuator/health
```

**🔒 인증 필요 엔드포인트**
```
GET http://localhost:8080/test/protected
Headers: Authorization: Bearer {JWT_TOKEN}

GET http://localhost:8080/gateway/users/health
Headers: Authorization: Bearer {JWT_TOKEN}
```

### 🧪 테스트 시나리오

#### 정상 인증 테스트
```
Method: GET
URL: http://localhost:8080/test/protected
Headers:
  Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIs...
  
Expected Response: 200 OK
{
  "message": "JWT 토큰이 유효합니다",
  "user": "user@example.com",
  "authorities": ["USER"],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

#### 토큰 없이 접근
```
Expected Response: 401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Invalid or missing JWT token"
}
```

### 🚨 문제 해결

#### "Unsupported algorithm of HS256" 오류
**해결됨**: 이제 로컬 환경에서 테스트용 HS256 토큰을 지원합니다.
- `application-local.yml`에서 `jwt.test-mode: true` 설정 확인

#### 401 Unauthorized 오류
1. **토큰 유효성 확인**: JWT 토큰이 만료되지 않았는지 확인
2. **Audience 확인**: audience가 `https://api.api-bridge.com`인지 확인
3. **Bearer 접두사**: Authorization 헤더에 "Bearer " 접두사 포함

### 🔍 모니터링 및 로깅

#### 로그 확인
```bash
# 애플리케이션 로그 확인
tail -f logs/application.log

# JWT 관련 로그 필터링
grep "JWT" logs/application.log
```

#### Actuator 엔드포인트
```
GET http://localhost:8080/actuator/health    # 헬스 체크
GET http://localhost:8080/actuator/metrics   # 메트릭
GET http://localhost:8080/actuator/gateway   # 게이트웨이 라우팅 정보
```

### 🔗 유용한 링크

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **테스트 페이지**: http://localhost:8080/public/auth-test.html
- **헬스 체크**: http://localhost:8080/actuator/health
- **Auth0 설정**: http://localhost:8080/public/auth0-config

### 🔐 보안 고려사항

- JWT 토큰은 민감한 정보이므로 로그에 출력되지 않도록 마스킹 처리됨
- 프로덕션 환경에서는 `jwt.test-mode: false`로 설정
- Auth0 Client Secret은 서버에서만 사용 (클라이언트에 노출 금지)

---

## 📝 결론

이 통합 가이드는 API Gateway의 핵심 기능들을 체계적으로 정리한 문서입니다. 각 기능은 보안과 성능을 고려하여 설계되었으며, 마이크로서비스 아키텍처에서 안정적이고 효율적인 API Gateway 솔루션을 제공합니다.

### 주요 이점
1. **보안 강화**: 다층 보안 체계로 악성 트래픽 차단
2. **안정성 향상**: 서킷브레이커를 통한 장애 전파 방지
3. **일관성 보장**: 표준화된 응답 형식으로 개발 효율성 향상
4. **추적성 제공**: Request ID를 통한 분산 추적 지원
5. **모니터링 강화**: 다양한 메트릭과 로그를 통한 운영 효율성

이제 완전하고 안전한 API Gateway 환경에서 마이크로서비스를 운영할 준비가 완료되었습니다! 🚀