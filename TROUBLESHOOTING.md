# Spring Cloud Gateway + Auth0 + Eureka 트러블슈팅 가이드

## 목차
1. [JWT 토큰 관련 문제](#jwt-토큰-관련-문제)
2. [CORS 관련 문제](#cors-관련-문제)
3. [Eureka 서비스 디스커버리 문제](#eureka-서비스-디스커버리-문제)
4. [TokenRelay 관련 문제](#tokenrelay-관련-문제)
5. [OAuth2 Login과 Resource Server 충돌](#oauth2-login과-resource-server-충돌)
6. [성능 및 타임아웃 문제](#성능-및-타임아웃-문제)

---

## JWT 토큰 관련 문제

### 1. 401 Unauthorized - "JWT token required"

**증상:**
```json
{
  "error": "unauthorized",
  "message": "JWT token required",
  "status": 401
}
```

**원인:**
- Authorization 헤더가 누락됨
- JWT 토큰 형식이 잘못됨 (`Bearer ` 접두사 누락)
- 토큰이 만료됨
- 잘못된 audience 또는 issuer

**해결책:**
```javascript
// 올바른 헤더 형식
const headers = {
  'Authorization': `Bearer ${accessToken}`,
  'Content-Type': 'application/json'
};

// Auth0에서 토큰 획득 시 audience 파라미터 포함
const token = await auth0Client.getTokenSilently({
  audience: 'https://api.yourdomain.com'
});
```

**디버깅:**
```bash
# JWT 토큰 디코딩 (jwt.io 또는 CLI 도구 사용)
echo "your-jwt-token" | base64 -d

# 로그 레벨 조정
logging:
  level:
    org.springframework.security.oauth2.jwt: DEBUG
    com.nimbusds: DEBUG
```

### 2. 403 Insufficient Scope - "insufficient_scope"

**증상:**
```json
{
  "error": "insufficient_scope",
  "message": "Insufficient permissions to access this resource",
  "status": 403
}
```

**원인:**
- JWT 토큰에 필요한 권한(scope/permission)이 없음
- Auth0에서 권한이 올바르게 설정되지 않음
- 권한 클레임 이름이 잘못됨

**해결책:**

1. **Auth0 Dashboard 설정 확인:**
   - API > Permissions에서 필요한 권한 추가
   - User > Roles에서 사용자에게 역할 할당
   - Rules/Actions에서 토큰에 권한 클레임 추가

2. **권한 클레임 추가 (Auth0 Action):**
```javascript
exports.onExecutePostLogin = async (event, api) => {
  const namespace = 'https://api.yourdomain.com/';
  
  if (event.authorization) {
    // 권한 추가
    api.accessToken.setCustomClaim(`${namespace}permissions`, event.authorization.permissions);
    // 역할 추가
    api.accessToken.setCustomClaim(`${namespace}roles`, event.authorization.roles);
  }
};
```

3. **SecurityConfig 권한 매핑 확인:**
```java
.pathMatchers("/api/users/**").hasAnyAuthority("read:users", "write:users")
```

### 3. JWT 서명 검증 실패

**증상:**
```
An error occurred while attempting to decode the Jwt: Signed JWT rejected: Invalid signature
```

**원인:**
- JWKS 엔드포인트에 접근할 수 없음
- 네트워크 연결 문제
- Auth0 도메인 설정 오류

**해결책:**
```yaml
# application.yml - 올바른 issuer URI 설정
auth0:
  issuerUri: https://your-domain.auth0.com/  # 끝에 / 포함
```

```java
// SecurityConfig - 타임아웃 설정
@Bean
public ReactiveJwtDecoder reactiveJwtDecoder() {
    return NimbusReactiveJwtDecoder
        .withJwkSetUri(issuer + ".well-known/jwks.json")
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .cache(Duration.ofMinutes(10))  // 캐시 설정
        .build();
}
```

---

## CORS 관련 문제

### 1. CORS Preflight 요청 실패

**증상:**
```
Access to fetch at 'http://localhost:8080/api/users' from origin 'http://localhost:3000' 
has been blocked by CORS policy: Response to preflight request doesn't pass access control check
```

**원인:**
- OPTIONS 요청에 대한 허용 설정 누락
- allowCredentials와 allowedOrigins="*" 동시 사용

**해결책:**
```yaml
# application.yml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"
              - "https://yourdomain.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - "*"
            allowCredentials: true
            maxAge: 3600
```

```java
// SecurityConfig - OPTIONS 요청 허용
.pathMatchers(HttpMethod.OPTIONS).permitAll()
```

### 2. 인증 쿠키 전송 문제

**증상:**
- 로그인 후 쿠키가 전송되지 않음
- 브라우저에서 쿠키가 설정되지 않음

**해결책:**
```javascript
// 프론트엔드 - fetch 요청 시 credentials 포함
fetch('http://localhost:8080/api/users', {
  method: 'GET',
  credentials: 'include',  // 쿠키 포함
  headers: {
    'Content-Type': 'application/json'
  }
});

// axios 설정
axios.defaults.withCredentials = true;
```

---

## Eureka 서비스 디스커버리 문제

### 1. 서비스 등록 실패

**증상:**
```
Cannot execute request on any known server
```

**원인:**
- Eureka 서버가 실행되지 않음
- 네트워크 연결 문제
- 잘못된 Eureka 서버 URL

**해결책:**
```yaml
# application.yml - Eureka 클라이언트 설정
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    # 연결 재시도 설정
    eureka-server-connect-timeout-seconds: 5
    eureka-server-read-timeout-seconds: 8
  instance:
    # 헬스체크 활성화
    health-check-url-path: /actuator/health
    lease-renewal-interval-in-seconds: 10
```

**디버깅:**
```bash
# Eureka 서버 상태 확인
curl http://localhost:8761/eureka/apps

# 서비스 등록 상태 확인
curl http://localhost:8761/eureka/apps/API-GATEWAY
```

### 2. 로드 밸런싱 실패

**증상:**
```
No instances available for USER-SERVICE
```

**원인:**
- 서비스가 Eureka에 등록되지 않음
- 서비스 이름 불일치
- 헬스체크 실패

**해결책:**
```yaml
# 서비스 이름 일치 확인
spring:
  application:
    name: USER-SERVICE  # 대소문자 주의
```

```java
// Gateway 라우트 설정 확인
- id: user-service
  uri: lb://USER-SERVICE  # 정확한 서비스 이름 사용
```

---

## TokenRelay 관련 문제

### 1. 토큰이 다운스트림 서비스로 전달되지 않음

**증상:**
- API Gateway에서는 인증 성공
- 마이크로서비스에서 401 Unauthorized 발생

**원인:**
- TokenRelay 필터 누락
- 필터 순서 문제
- OAuth2 Client 설정 누락

**해결책:**
```yaml
# application.yml - TokenRelay 필터 추가
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://USER-SERVICE
          filters:
            - TokenRelay=  # 중요: TokenRelay 필터 추가
```

```java
// SecurityConfig - OAuth2 Client 설정 필요
@Bean
public SecurityWebFilterChain oAuth2LoginSecurityFilterChain(
        ServerHttpSecurity http,
        ReactiveClientRegistrationRepository clientRegistrationRepository) {
    // OAuth2 Client 설정 포함
}
```

### 2. 토큰 형식 문제

**증상:**
- 토큰이 전달되지만 형식이 잘못됨
- Bearer 접두사 누락

**해결책:**
```java
// 커스텀 TokenRelay 필터 (필요한 경우)
@Component
public class CustomTokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {
    
    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(principal -> {
                    String token = principal.getToken().getTokenValue();
                    ServerHttpRequest request = exchange.getRequest().mutate()
                        .header("Authorization", "Bearer " + token)
                        .build();
                    return exchange.mutate().request(request).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
        };
    }
}
```

---

## OAuth2 Login과 Resource Server 충돌

### 1. ClassCastException 발생

**증상:**
```
java.lang.ClassCastException: OAuth2LoginAuthenticationToken cannot be cast to JwtAuthenticationToken
```

**원인:**
- 단일 SecurityWebFilterChain에서 OAuth2 Login과 Resource Server 동시 사용
- 인증 토큰 타입 충돌

**해결책:**
```java
// SecurityConfig - 두 개의 분리된 필터 체인 사용
@Bean
@Order(1)
public SecurityWebFilterChain oAuth2LoginSecurityFilterChain(...) {
    return http
        .securityMatcher(exchange -> {
            String path = exchange.getRequest().getPath().value();
            return path.startsWith("/auth/") || path.startsWith("/oauth2/");
        })
        .oauth2Login(...)
        .build();
}

@Bean
@Order(2)
public SecurityWebFilterChain jwtResourceServerSecurityFilterChain(...) {
    return http
        .securityMatcher(exchange -> {
            String path = exchange.getRequest().getPath().value();
            return !path.startsWith("/auth/") && !path.startsWith("/oauth2/");
        })
        .oauth2ResourceServer(...)
        .build();
}
```

### 2. 인증 경로 충돌

**증상:**
- 로그인 페이지가 표시되지 않음
- API 요청이 로그인으로 리다이렉트됨

**해결책:**
```java
// 명확한 경로 분리
.securityMatcher(exchange -> {
    String path = exchange.getRequest().getPath().value();
    // OAuth2 Login 경로만 처리
    return path.startsWith("/auth/") || 
           path.startsWith("/oauth2/") || 
           path.startsWith("/login/");
})
```

---

## 성능 및 타임아웃 문제

### 1. JWT 검증 지연

**증상:**
- 첫 번째 요청이 매우 느림
- JWKS 조회 타임아웃

**해결책:**
```java
// JWT 디코더 캐시 설정
@Bean
public ReactiveJwtDecoder reactiveJwtDecoder() {
    return NimbusReactiveJwtDecoder
        .withJwkSetUri(issuer + ".well-known/jwks.json")
        .cache(Duration.ofMinutes(10))  // 10분 캐시
        .build();
}
```

```yaml
# application.yml - 타임아웃 설정
server:
  netty:
    connection-timeout: 2s
    idle-timeout: 15s

spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 1000
        response-timeout: 5s
```

### 2. Eureka 서비스 검색 지연

**증상:**
- 서비스 시작 후 라우팅이 동작하지 않음
- 서비스 등록까지 시간이 오래 걸림

**해결책:**
```yaml
# Eureka 클라이언트 최적화
eureka:
  client:
    registry-fetch-interval-seconds: 5  # 기본값 30초 → 5초
    initial-instance-info-replication-interval-seconds: 5
  instance:
    lease-renewal-interval-in-seconds: 10  # 기본값 30초 → 10초
```

---

## 환경 변수 설정 예시

```bash
# .env 파일 또는 환경 변수
AUTH0_ISSUER_URI=https://your-domain.auth0.com/
AUTH0_AUDIENCE=https://api.yourdomain.com
AUTH0_CLIENT_ID=your-client-id
AUTH0_CLIENT_SECRET=your-client-secret
AUTH0_LOGOUT_REDIRECT_URI=http://localhost:8080/auth/logout-success

EUREKA_SERVER_URL=http://localhost:8761/eureka/
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
```

## 로그 레벨 설정 (디버깅용)

```yaml
logging:
  level:
    # Spring Security
    org.springframework.security: DEBUG
    org.springframework.security.oauth2.jwt: DEBUG
    org.springframework.security.oauth2.client: DEBUG
    
    # Spring Cloud Gateway
    org.springframework.cloud.gateway: DEBUG
    org.springframework.cloud.gateway.filter: DEBUG
    
    # Eureka
    com.netflix.eureka: DEBUG
    com.netflix.discovery: DEBUG
    
    # JWT 라이브러리
    com.nimbusds: DEBUG
    
    # HTTP 클라이언트
    reactor.netty.http.client: DEBUG
```

## 헬스체크 엔드포인트

```bash
# API Gateway 상태 확인
curl http://localhost:8080/actuator/health

# Eureka 서버 상태 확인
curl http://localhost:8761/actuator/health

# 등록된 서비스 목록 확인
curl http://localhost:8761/eureka/apps

# Gateway 라우트 확인
curl http://localhost:8080/actuator/gateway/routes
```

이 가이드를 참고하여 문제를 단계적으로 해결해보세요. 추가적인 문제가 발생하면 로그를 자세히 확인하고 각 컴포넌트의 상태를 점검하는 것이 중요합니다.

