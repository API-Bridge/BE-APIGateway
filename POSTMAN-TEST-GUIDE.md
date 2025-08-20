# API Gateway Postman 테스트 가이드 🚀

이 가이드는 Redis와 Kafka가 활성화된 API Gateway의 모든 기능을 Postman으로 테스트하는 방법을 설명합니다.

## 🔧 사전 준비

### 1. Docker Compose 실행
```bash
# 프로젝트 루트에서 실행
docker-compose up -d

# 서비스 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f api-gateway
```

### 2. 서비스 대기
모든 서비스가 준비될 때까지 약 30-60초 대기:
- ✅ Redis: 포트 6380
- ✅ Kafka: 포트 9093
- ✅ Kafka UI: 포트 8081
- ✅ API Gateway: 포트 8080

### 3. Postman 환경 설정
기존 환경 파일 사용: `postman/API-Gateway-Local.postman_environment.json`

추가 환경 변수:
```json
{
  "key": "base_url",
  "value": "http://localhost:8080"
},
{
  "key": "redis_enabled",
  "value": "true"
},
{
  "key": "kafka_enabled", 
  "value": "true"
}
```

## 📋 테스트 시나리오

### 1단계: 기본 헬스체크 ❤️

#### 1.1 애플리케이션 상태 확인
```http
GET {{base_url}}/actuator/health
```
**기대 응답**: `200 OK`
```json
{
  "status": "UP",
  "components": {
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" },
    "circuitBreakers": { "status": "UP" }
  }
}
```

#### 1.2 공개 엔드포인트 테스트
```http
GET {{base_url}}/public/health
```

#### 1.3 Swagger UI 접속
```http
GET {{base_url}}/swagger-ui.html
```

---

### 2단계: 인증 시스템 테스트 🔐

#### 2.1 토큰 없이 보호된 리소스 접근 (401)
```http
GET {{base_url}}/api/users/profile
```
**기대 응답**: `401 Unauthorized`

#### 2.2 Auth0 로그인 페이지 접속
```http
GET {{base_url}}/auth/login
```
**결과**: Auth0 로그인 페이지로 리다이렉트

#### 2.3 테스트 JWT 토큰 생성 (개발용)
```http
POST {{base_url}}/api/test/generate-token
Content-Type: application/json

{
  "sub": "test-user-123",
  "email": "test@example.com",
  "permissions": ["read:users", "write:users"]
}
```
**응답에서 `access_token` 저장**

#### 2.4 JWT 토큰으로 보호된 리소스 접근
```http
GET {{base_url}}/api/users/profile
Authorization: Bearer {{access_token}}
```
**기대 응답**: `200 OK` (Mock 응답)

---

### 3단계: Rate Limiting 테스트 ⚡

#### 3.1 Rate Limit 헤더 확인
```http
GET {{base_url}}/gateway/ratelimit/test
Authorization: Bearer {{access_token}}
```
**응답 헤더 확인**:
```
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 1640995200
```

#### 3.2 Rate Limit 초과 테스트
**Postman Runner 사용**:
- Collection: Rate Limit Test
- Iterations: 10
- Delay: 100ms

6번째 요청부터:
```json
{
  "type": "about:blank",
  "title": "Rate limit exceeded",
  "status": 429,
  "detail": "Too many requests. Please slow down and try again later",
  "instance": "{{requestId}}"
}
```

#### 3.3 서비스별 Rate Limit 테스트

**User Service (관대한 정책: 20req/s)**
```http
GET {{base_url}}/gateway/users/test
Authorization: Bearer {{access_token}}
```

**AI Service (엄격한 정책: 5req/s, 2토큰/요청)**
```http
GET {{base_url}}/gateway/aifeature/test  
Authorization: Bearer {{access_token}}
```

---

### 4단계: 차단 시스템 테스트 🚫

#### 4.1 사용자 차단
```http
POST {{base_url}}/internal/block/user
Content-Type: application/json
Authorization: Bearer {{admin_token}}

{
  "userId": "test-user-123",
  "duration": "PT30M",
  "reason": "테스트 차단"
}
```

#### 4.2 차단된 사용자 요청 테스트
```http
GET {{base_url}}/api/users/profile
Authorization: Bearer {{access_token}}
```
**기대 응답**: `403 Forbidden`
```json
{
  "type": "about:blank", 
  "title": "Access Blocked",
  "status": 403,
  "detail": "Your access has been blocked: 테스트 차단"
}
```

#### 4.3 IP 차단 테스트
```http
POST {{base_url}}/internal/block/ip
Content-Type: application/json
Authorization: Bearer {{admin_token}}

{
  "ipAddress": "127.0.0.1",
  "duration": "PT5M",
  "reason": "테스트 IP 차단"
}
```

#### 4.4 차단 해제
```http
DELETE {{base_url}}/internal/block/user/test-user-123
Authorization: Bearer {{admin_token}}
```

---

### 5단계: 로그인 시도 모니터링 테스트 🔒

#### 5.1 의도적 인증 실패 (5회)
```http
GET {{base_url}}/api/users/profile
Authorization: Bearer invalid-token-{{$randomInt}}
```
**5회 반복 실행**

#### 5.2 자동 차단 확인
6번째 요청:
```http
GET {{base_url}}/api/users/profile
Authorization: Bearer {{access_token}}
```
**기대 응답**: `403 Forbidden` (자동 차단됨)

#### 5.3 로그인 시도 통계 확인
```http
GET {{base_url}}/internal/login-attempts/stats/test-user-123
Authorization: Bearer {{admin_token}}
```

---

### 6단계: Circuit Breaker 테스트 ⚡

#### 6.1 Circuit Breaker 상태 조회
```http
GET {{base_url}}/public/circuit-breaker/status
```

#### 6.2 특정 서비스 Circuit Breaker 상태
```http
GET {{base_url}}/public/circuit-breaker/status/userSvcCb
```

#### 6.3 Fallback 응답 테스트 (서비스 다운 시뮬레이션)
```http
GET {{base_url}}/fallback/user-service
```
**기대 응답**: `503 Service Unavailable`

---

### 7단계: 표준 응답 형식 테스트 📋

#### 7.1 성공 응답 래핑 확인
```http
GET {{base_url}}/api/users/profile
Authorization: Bearer {{access_token}}
```
**기대 응답 형식**:
```json
{
  "success": true,
  "code": "SUCCESS", 
  "message": "요청이 성공적으로 처리되었습니다",
  "data": { /* 원본 응답 */ },
  "meta": {
    "requestId": "uuid",
    "durationMs": 123,
    "gateway": "API-Gateway",
    "version": "1.0"
  }
}
```

#### 7.2 에러 응답 래핑 확인
```http
GET {{base_url}}/api/nonexistent/endpoint
Authorization: Bearer {{access_token}}
```
**기대 응답 형식**:
```json
{
  "success": false,
  "code": "NOT_FOUND",
  "message": "요청하신 리소스를 찾을 수 없습니다",
  "error": {
    "type": "ROUTING",
    "details": {
      "httpStatus": 404,
      "originalResponse": "..."
    },
    "requestId": "uuid"
  },
  "meta": { /* ... */ }
}
```

---

### 8단계: 모니터링 엔드포인트 테스트 📊

#### 8.1 Prometheus 메트릭
```http
GET {{base_url}}/actuator/prometheus
```

#### 8.2 Gateway 라우트 정보
```http
GET {{base_url}}/actuator/gateway/routes
```

#### 8.3 Circuit Breaker 메트릭
```http
GET {{base_url}}/actuator/circuitbreakers
```

---

## 🔍 Redis/Kafka 확인 방법

### Redis 접속 및 확인

#### Docker 컨테이너 내부 접속
```bash
# Redis CLI 접속
docker exec -it api-gateway-redis redis-cli

# 또는 직접 포트로 접속
redis-cli -h localhost -p 6380
```

#### Rate Limiting 상태 확인
```redis
# 모든 Rate Limit 키 조회
KEYS *rate*

# 특정 사용자의 Rate Limit 상태
GET "request_rate_limiter.user:test-user-123.tokens"
GET "request_rate_limiter.user:test-user-123.timestamp"

# TTL 확인
TTL "request_rate_limiter.user:test-user-123.tokens"
```

#### 차단 상태 확인
```redis
# 모든 차단 키 조회
KEYS blocked:*

# 특정 사용자 차단 상태
GET "blocked:user:test-user-123"
GET "blocked:ip:127.0.0.1"

# 로그인 시도 추적
KEYS login_attempts:*
GET "login_attempts:test-user-123"
```

### Kafka 로그 확인

#### Kafka UI 접속
브라우저에서 http://localhost:8081 접속
- Topics → logs.gateway 클릭
- Messages 탭에서 실시간 로그 확인

#### Kafka CLI로 로그 확인
```bash
# Docker 컨테이너 내부 접속
docker exec -it api-gateway-kafka bash

# 토픽 목록 확인
kafka-topics --list --bootstrap-server localhost:9092

# 실시간 로그 메시지 확인
kafka-console-consumer --topic logs.gateway --bootstrap-server localhost:9092 --from-beginning

# 최근 10개 메시지만 확인
kafka-console-consumer --topic logs.gateway --bootstrap-server localhost:9092 --max-messages 10
```

#### 로그 메시지 형식 예시
```json
{
  "requestId": "uuid",
  "eventType": "REQUEST_START",
  "timestamp": "2024-01-01T00:00:00Z",
  "method": "GET",
  "url": "/api/users/profile",
  "clientIp": "172.19.0.1",
  "userId": "test-user-123",
  "userAgent": "PostmanRuntime/7.28.4",
  "routeId": "user-service-local",
  "publicApiName": "Users API",
  "headers": {
    "authorization": "Bearer ****",
    "user-agent": "PostmanRuntime/7.28.4"
  }
}
```

---

## 🚨 문제 해결

### 일반적인 문제들

#### 1. 서비스 시작 실패
```bash
# 모든 서비스 중지 후 재시작
docker-compose down
docker-compose up -d

# 개별 서비스 로그 확인
docker-compose logs redis
docker-compose logs kafka
docker-compose logs api-gateway
```

#### 2. Redis 연결 실패
```bash
# Redis 상태 확인
docker exec api-gateway-redis redis-cli ping

# 포트 확인
netstat -an | grep 6380
```

#### 3. Kafka 연결 실패
```bash
# Kafka 브로커 상태 확인
docker exec api-gateway-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# 토픽 생성 (필요시)
docker exec api-gateway-kafka kafka-topics --create --topic logs.gateway --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

#### 4. 인증 문제
- Auth0 설정 확인: .env 파일의 CLIENT_ID, CLIENT_SECRET, ISSUER_URI, AUDIENCE
- 토큰 만료 확인: 새 토큰 생성
- 네트워크 확인: Auth0 JWKS 엔드포인트 접근 가능 여부

#### 5. Rate Limiting 작동 안 함
- Redis 연결 상태 확인
- 환경변수 확인: `redis.enabled=true`
- Rate Limiter 빈 로딩 확인: 애플리케이션 로그

---

## 📈 성능 테스트

### 부하 테스트 시나리오

#### Postman Collection Runner 설정
- **Iterations**: 100
- **Delay**: 100ms
- **Environment**: API-Gateway-Local

#### 테스트 메트릭 모니터링
1. **Redis 메모리 사용량**
   ```bash
   docker exec api-gateway-redis redis-cli info memory
   ```

2. **Kafka 처리량**
   ```bash
   # Kafka UI에서 확인: http://localhost:8081
   # Topics → logs.gateway → Overview
   ```

3. **API Gateway 메트릭**
   ```http
   GET {{base_url}}/actuator/metrics/http.server.requests
   GET {{base_url}}/actuator/metrics/resilience4j.circuitbreaker
   ```

---

이 가이드를 통해 API Gateway의 모든 기능을 체계적으로 테스트할 수 있습니다. 문제 발생 시 로그와 모니터링 도구를 활용하여 신속하게 문제를 파악하고 해결할 수 있습니다.