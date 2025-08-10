# API Gateway 테스트 가이드

## 🚀 애플리케이션 실행

### 1. 사전 준비사항
- Java 17 이상
- Redis 서버 (선택사항 - Rate Limiting 테스트용)

### 2. 애플리케이션 시작
```bash
# 개발 프로필로 실행 (테스트 모드 활성화)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 JAR 파일로 실행
./gradlew build -x test
java -jar build/libs/APIGatewaySvc-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

서버가 시작되면 `http://localhost:8080`에서 접근 가능합니다.

## 📖 Swagger UI를 통한 테스트

### 1. Swagger UI 접속
- URL: http://localhost:8080/swagger-ui.html
- 모든 API 엔드포인트를 브라우저에서 직접 테스트 가능

### 2. JWT 토큰 획득
1. **JWT Token Generator** 섹션에서 `GET /public/jwt/tokens` 호출
2. 응답에서 `valid` 토큰 복사
3. Swagger UI 우측 상단 **Authorize** 버튼 클릭
4. `Bearer {복사한토큰}` 형식으로 입력

### 3. 테스트 시나리오
- **Health Check**: `/test/health` (인증 불필요)
- **보호된 엔드포인트**: `/test/protected` (JWT 토큰 필요)
- **Rate Limiting**: `/test/rate-limit-test` (빠르게 여러 번 호출)
- **마이크로서비스 라우팅**: `/api/users/*`, `/api/aifeature/*` 등

## 📮 Postman을 통한 테스트

### 1. 컬렉션 가져오기
1. Postman 실행
2. **Import** 버튼 클릭
3. `postman/API-Gateway-Test.postman_collection.json` 파일 선택
4. `postman/API-Gateway-Local.postman_environment.json` 환경 파일도 가져오기

### 2. 환경 설정
1. 우측 상단에서 **API Gateway Local Environment** 선택
2. Variables 탭에서 `base_url`이 `http://localhost:8080`으로 설정되어 있는지 확인

### 3. 자동화된 테스트 실행
1. **"1. JWT 토큰 생성"** 폴더의 **"테스트용 JWT 토큰들 조회"** 실행
   - JWT 토큰이 자동으로 환경변수에 설정됨
2. **"3. JWT 인증 테스트"** 폴더의 요청들 실행
3. **"4. Rate Limiting 테스트"** 폴더에서 연속 호출로 429 에러 확인

### 4. 주요 테스트 케이스

#### 인증 테스트
```http
GET /test/protected
Authorization: Bearer {jwt_token}
```

#### Rate Limiting 테스트
```http
GET /test/rate-limit-test
Authorization: Bearer {jwt_token}
```
*이 요청을 빠르게 10회 이상 호출하여 429 에러 확인*

#### 마이크로서비스 라우팅 테스트
```http
# User Service (개발환경에서는 httpbin.org로 라우팅)
GET /api/users/profile
Authorization: Bearer {jwt_token}

# AI Feature Service  
GET /api/aifeature/chat
Authorization: Bearer {jwt_token}
```

## 🔧 사용 가능한 JWT 토큰

### 토큰 종류 (`GET /public/jwt/tokens` 응답)
- **valid**: 정상적인 JWT 토큰 (일반 사용자)
- **admin**: 관리자 권한을 가진 JWT 토큰  
- **readonly**: 읽기 권한만 있는 JWT 토큰
- **expired**: 만료된 JWT 토큰 (401 에러 테스트용)
- **invalid_audience**: 잘못된 오디언스 JWT 토큰 (401 에러 테스트용)

### 커스텀 토큰 생성
```http
POST /public/jwt/generate
Content-Type: application/json

{
  "userId": "custom-user-123",
  "permissions": ["read:users", "write:users", "admin:system"]
}
```

## 🚨 예상 응답 코드

### 성공 케이스
- **200 OK**: 정상 처리
- **404 Not Found**: 개발환경 Mock 서비스 응답 (정상)

### 에러 케이스
- **401 Unauthorized**: JWT 토큰 없음/만료/무효
- **403 Forbidden**: 권한 부족
- **429 Too Many Requests**: Rate Limit 초과
- **503 Service Unavailable**: 다운스트림 서비스 불가

## 🔍 추가 모니터링

### Actuator 엔드포인트
- **Health Check**: `GET /actuator/health`
- **Gateway Routes**: `GET /actuator/gateway/routes`
- **Metrics**: `GET /actuator/metrics`

### 로그 확인
```bash
# 애플리케이션 로그에서 Request ID 및 에러 추적
tail -f logs/api-gateway.log
```

## ⚠️ 주의사항

1. **테스트 모드**: 현재 설정은 개발/테스트 전용입니다
2. **Redis**: Rate Limiting 테스트를 위해서는 Redis 서버 필요
3. **실제 Auth0**: 운영환경에서는 실제 Auth0 JWT 토큰 사용 필요
4. **보안**: 테스트용 JWT 생성 기능은 운영환경에서 비활성화되어야 함

## 🛠️ 트러블슈팅

### 401 Unauthorized
- JWT 토큰이 올바르게 설정되었는지 확인
- 토큰이 만료되지 않았는지 확인
- `Authorization: Bearer {token}` 헤더 형식 확인

### 429 Too Many Requests  
- 정상 동작 (Rate Limiting 기능 확인됨)
- 1분 정도 대기 후 다시 시도

### 503 Service Unavailable
- 개발환경에서는 실제 마이크로서비스 대신 httpbin.org 사용
- 네트워크 연결 상태 확인