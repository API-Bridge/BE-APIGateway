# API Gateway Service

Spring Boot + Spring Cloud Gateway 기반의 API Gateway 서비스입니다.  
Auth0 JWT 인증을 통해 마이크로서비스들에 대한 통합 인증/인가 및 라우팅을 제공합니다.

## 🔧 기술 스택

- **Framework**: Spring Boot 3.5.4 + Spring Cloud Gateway
- **Java**: 17 (Amazon Corretto)
- **Authentication**: Auth0 JWT (OAuth2 Resource Server)
- **Rate Limiting**: Redis
- **Monitoring**: Prometheus + Actuator
- **Architecture**: Reactive WebFlux

## 📦 주요 기능

- **JWT 인증/인가**: Auth0 기반 JWT 토큰 검증
- **마이크로서비스 라우팅**: 경로 기반 요청 라우팅 및 Load Balancing
- **Rate Limiting**: Redis 기반 사용자별/IP별 요청 제한
- **Circuit Breaker**: 다운스트림 서비스 장애 대응
- **Request Tracing**: 모든 요청에 대한 고유 ID 추적
- **표준 에러 응답**: RFC 7807 Problem Details 표준 준수
- **CORS 지원**: Cross-Origin Resource Sharing 설정

## 🏗️ 마이크로서비스 라우팅

### ✅ 1단계: 기본 인증/인가 (완료)
- Auth0 JWT 토큰 검증
- `/public/**`, `/actuator/**` 공개 경로
- 표준 JSON 에러 응답

### ✅ 2단계: 서비스 라우팅 + Rate Limiting (완료)
- `/api/users/**` → User Service (관대한 정책: 20req/s)
- `/api/apimgmt/**` → API Management Service (관리 정책: 15req/s)
- `/api/customapi/**` → Custom API Management Service (관리 정책: 15req/s)
- `/api/aifeature/**` → AI Feature Service (엄격한 정책: 5req/s, 2토큰/요청)
- `/api/sysmgmt/**` → System Management Service (관리 정책: 15req/s)

**주요 기능:**
- **StripPrefix=2**: `/api/users/profile` → `/profile`로 변환
- **사용자별 Rate Limiting**: JWT sub 클레임 기반 개별 제한
- **서비스별 Circuit Breaker**: 장애 전파 방지
- **429 Too Many Requests**: Rate Limit 초과 시 표준 응답

## 🚀 로컬 개발 환경 구성

### 1. 필수 환경변수 설정
```bash
# .env 파일 생성
AUTH0_ISSUER_URI=https://your-domain.auth0.com/
AUTH0_AUDIENCE=https://api.your-service.com
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379
```

### 2. Redis 실행 (Rate Limiting용)
```bash
# Docker로 Redis 실행
docker run -d --name redis -p 6379:6379 redis:alpine

# 또는 로컬 Redis 사용
brew install redis  # macOS
redis-server
```

### 3. 애플리케이션 실행
```bash
# Gradle로 실행
./gradlew bootRun

# 또는 IDE에서 APIGatewaySvcApplication 실행
```

### 4. 동작 확인
```bash
# 공개 엔드포인트 (인증 불필요)
curl http://localhost:8080/public/health

# 보호된 엔드포인트 (JWT 토큰 필요)
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8080/api/users/profile
```

## 🔒 라우팅 및 인증 테스트

### 1. Auth0 토큰 발급 (개발용)
```bash
# Auth0 Management API를 통한 토큰 발급 예시
curl -X POST https://your-domain.auth0.com/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "YOUR_CLIENT_ID",
    "client_secret": "YOUR_CLIENT_SECRET", 
    "audience": "https://api.your-service.com",
    "grant_type": "client_credentials"
  }'
```

### 2. 마이크로서비스 라우팅 테스트
```bash
# User Service 라우팅 (관대한 Rate Limit)
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8080/api/users/profile
# → User Service의 /profile 엔드포인트로 라우팅

# AI Feature Service 라우팅 (엄격한 Rate Limit)  
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8080/api/aifeature/chat
# → AI Feature Service의 /chat 엔드포인트로 라우팅

# API Management Service 라우팅
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8080/api/apimgmt/apis
# → API Management Service의 /apis 엔드포인트로 라우팅
```

### 3. Rate Limiting 테스트
```bash
# 연속 요청으로 Rate Limit 테스트
for i in {1..25}; do
  curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
       http://localhost:8080/api/aifeature/test
  echo "Request $i"
  sleep 0.1
done

# Rate Limit 초과 시 429 응답:
# {
#   "type": "about:blank",
#   "title": "Rate limit exceeded", 
#   "status": 429,
#   "detail": "Too many requests. Please slow down and try again later",
#   "instance": "12345-67890-abcdef"
# }
```

### 4. 에러 케이스 테스트
```bash
# 토큰 없이 접근 (401 Unauthorized)
curl http://localhost:8080/api/users/profile

# 잘못된 토큰 (401 Unauthorized) 
curl -H "Authorization: Bearer invalid-token" \
     http://localhost:8080/api/users/profile

# 존재하지 않는 서비스 (404 Not Found)
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     http://localhost:8080/api/nonexistent/endpoint
```

## 📊 모니터링 및 관리

### Actuator 엔드포인트
```bash
# 헬스 체크
curl http://localhost:8080/actuator/health

# 메트릭 (Prometheus)
curl http://localhost:8080/actuator/prometheus

# Gateway 라우트 정보
curl http://localhost:8080/actuator/gateway/routes
```

### Swagger UI
- URL: http://localhost:8080/swagger-ui.html
- Gateway 자체 API 문서 확인

## 🧪 테스트

### 단위 테스트 실행
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행  
./gradlew test --tests="SecurityConfigTest"
./gradlew test --tests="ProblemDetailsUtilTest"
./gradlew test --tests="KeyResolverConfigTest"
```

### 통합 테스트 케이스
**1단계 (인증/인가):**
- ✅ JWT 토큰 검증 (유효/만료/오디언스 불일치)
- ✅ 공개 경로 접근 허용
- ✅ 보호된 경로 인증 필요
- ✅ RFC 7807 표준 에러 응답
- ✅ Request ID 추적
- ✅ 민감정보 마스킹

**2단계 (라우팅 + Rate Limiting):**
- ✅ 5개 마이크로서비스 라우팅 (StripPrefix=2)
- ✅ 사용자별 독립적인 Rate Limiting
- ✅ 서비스별 다른 Rate Limit 정책 적용
- ✅ 429 Too Many Requests 에러 응답
- ✅ Circuit Breaker 및 Fallback 처리
- ✅ Gateway 헤더 추가 (타임스탬프, 서비스명)
- ✅ 보안 헤더 제거 (Cookie 등)

## 🐳 Docker 배포

### 이미지 빌드
```bash
docker build -t api-gateway:latest .
```

### 컨테이너 실행
```bash
docker run -p 8080:8080 \
  -e AUTH0_ISSUER_URI="https://your-domain.auth0.com/" \
  -e AUTH0_AUDIENCE="https://api.your-service.com" \
  -e SPRING_REDIS_HOST="redis-host" \
  -e SPRING_REDIS_PORT="6379" \
  -e SPRING_PROFILES_ACTIVE="prod" \
  api-gateway:latest
```

### Docker Compose (개발용)
```yaml
version: '3.8'
services:
  api-gateway:
    build: .
    ports:
      - "8080:8080"
    environment:
      - AUTH0_ISSUER_URI=https://your-domain.auth0.com/
      - AUTH0_AUDIENCE=https://api.your-service.com
      - SPRING_REDIS_HOST=redis
    depends_on:
      - redis

  redis:
    image: redis:alpine
    ports:
      - "6379:6379"
```

## 🔧 설정

### 필수 환경변수
```bash
# Auth0 설정
AUTH0_ISSUER_URI=https://your-domain.auth0.com/
AUTH0_AUDIENCE=https://api.your-service.com

# Redis (Rate Limiting)
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

# 다운스트림 서비스
USER_SERVICE_URI=http://user-service:8080
APIMGMT_SERVICE_URI=http://api-management-service:8080
CUSTOMAPI_SERVICE_URI=http://custom-api-management-service:8080
AIFEATURE_SERVICE_URI=http://ai-feature-service:8080
SYSMGMT_SERVICE_URI=http://system-management-service:8080

# Rate Limiting 설정 (선택사항)
RATE_LIMIT_REPLENISH_RATE=10          # 기본 초당 토큰 보충 개수
RATE_LIMIT_BURST_CAPACITY=20          # 기본 버스트 허용 토큰 수
RATE_LIMIT_REQUESTED_TOKENS=1         # 기본 요청당 소비 토큰 수

# 기타
SPRING_PROFILES_ACTIVE=dev|prod
SERVER_PORT=8080
```

## 🏛️ 아키텍처 결정사항

### 1. 보안 설계
- **JWT 검증**: Auth0 JWKS 엔드포인트를 통한 서명 검증
- **Audience 검증**: 토큰 오남용 방지를 위한 오디언스 검증
- **Request ID**: 모든 요청에 고유 추적 ID 부여
- **민감정보 마스킹**: 에러 응답에서 JWT 토큰, 이메일 등 마스킹

### 2. JWK 캐싱 미사용 결정
**현재 구현**: Auth0 JWKS 엔드포인트를 실시간 조회
- **장점**: 키 로테이션 즉시 반영, 캐시 무효화 관리 불필요
- **단점**: 네트워크 지연, Auth0 의존성 증가
- **방어책**: 연결 타임아웃 설정, Circuit Breaker 적용

### 3. Rate Limiting 전략
**키 전략**: 사용자 ID 우선, 없으면 클라이언트 IP 사용
```
인증된 사용자: user:{jwt-sub}
익명 사용자: ip:{client-ip}
```
**서비스별 정책**:
- User Service: 20req/s (관대한 정책)
- AI Feature Service: 5req/s, 2토큰/요청 (엄격한 정책)
- Management Services: 15req/s (중간 정책)

**우회 방지**:
- X-Forwarded-For 헤더 검증
- 사설망 IP 필터링
- 키 길이 제한

### 4. Circuit Breaker 전략
**서비스별 설정**:
- User Service: 관대한 설정 (60% 실패율, 30초 열림)
- AI Feature Service: 민감한 설정 (30% 실패율, 2분 열림)
- Management Services: 중간 설정 (40% 실패율, 45초 열림)

### 5. 에러 처리 표준화
- **RFC 7807 Problem Details** 표준 준수
- **구조화된 JSON 응답** 제공
- **HTTP 상태 코드** 의미에 맞게 사용
- **Request ID 포함**으로 추적성 보장
- **429 Too Many Requests**: Rate Limit 초과 시 Retry-After 헤더 포함

## ✅ 구현 완료 (1-2단계)

### 완료된 기능
1. ✅ **Auth0 JWT 인증/인가** - 토큰 검증 및 권한 확인
2. ✅ **마이크로서비스 라우팅** - 5개 서비스로의 경로 기반 라우팅
3. ✅ **Redis Rate Limiting** - 사용자별/서비스별 요청 제한
4. ✅ **Circuit Breaker** - 서비스 장애 전파 방지
5. ✅ **에러 처리 표준화** - RFC 7807 Problem Details
6. ✅ **Request Tracing** - 요청별 고유 ID 추적
7. ✅ **보안 강화** - 민감정보 마스킹, 불필요한 헤더 제거

### 실제 라우팅 규칙 (StripPrefix=2)
```yaml
# 사용자 서비스 (관대한 Rate Limit: 20req/s)
/api/users/profile → http://user-service/profile
/api/users/settings → http://user-service/settings

# AI 기능 서비스 (엄격한 Rate Limit: 5req/s, 2토큰/요청)
/api/aifeature/chat → http://ai-feature-service/chat
/api/aifeature/analyze → http://ai-feature-service/analyze

# API 관리 서비스 (관리 정책: 15req/s)
/api/apimgmt/apis → http://api-management-service/apis
/api/customapi/custom → http://custom-api-service/custom
/api/sysmgmt/config → http://system-management-service/config
```

## 🚀 향후 개선 사항 (선택)

### 3단계 예상 기능
1. **동적 라우팅** - 서비스 디스커버리 연동
2. **JWT 캐싱** - 성능 최적화를 위한 토큰 캐싱
3. **메트릭 대시보드** - Grafana 연동 모니터링
4. **A/B 테스팅** - 트래픽 분할 라우팅
5. **API 버전 관리** - 헤더 기반 버전 라우팅

## 📝 문제 해결

### 자주 발생하는 문제들

1. **401 Unauthorized 에러**
   - Auth0 설정 확인: issuer-uri, audience
   - JWT 토큰 유효성 확인 (만료, 서명)
   - 네트워크: Auth0 JWKS 엔드포인트 접근 가능 여부

2. **429 Too Many Requests 에러**
   - Rate Limit 설정 확인: replenish-rate, burst-capacity  
   - Redis 연결 상태 확인
   - 사용자별 Rate Limit 키 확인 (user: vs ip:)
   
3. **503 Service Unavailable 에러** 
   - Redis 연결 상태 확인
   - Circuit Breaker 상태 확인
   - 다운스트림 서비스 상태 확인

4. **404 라우팅 오류**
   - 경로 패턴 확인: `/api/users/**` vs `/api/user/**`
   - StripPrefix 설정 확인: StripPrefix=2
   - 프로파일 확인: dev(Mock) vs prod(실제 서비스)

5. **CORS 에러**
   - allowedOriginPatterns 설정 확인
   - preflight 요청 허용 확인

### 로그 확인
```bash
# 인증 관련 로그
grep "Authentication" logs/api-gateway.log

# Rate Limiting 로그
grep -i "rate.*limit\|too.*many" logs/api-gateway.log

# 라우팅 관련 로그
grep "Routing\|StripPrefix" logs/api-gateway.log

# Circuit Breaker 상태
grep -i "circuit.*breaker\|fallback" logs/api-gateway.log

# 요청 추적
grep "requestId" logs/api-gateway.log  

# 에러 패턴 분석
grep "ERROR" logs/api-gateway.log | tail -50
```

### 개발/디버깅 팁
```bash
# Gateway 라우트 정보 확인
curl http://localhost:8080/actuator/gateway/routes

# Rate Limit 상태 확인 (Redis)
redis-cli
> keys *rate*
> get rate_limit_user:auth0|user123

# Circuit Breaker 상태 확인  
curl http://localhost:8080/actuator/circuitbreakers

# 실시간 로그 모니터링
tail -f logs/api-gateway.log | grep -E "(Rate|Circuit|Auth)"
```