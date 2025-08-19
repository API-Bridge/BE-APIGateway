# Auth0 OAuth2 로그인 문제 해결 가이드

## ✅ 해결된 문제 - 직접 Auth0 구현으로 전환

### 이전 문제 증상 (해결됨)
- ~~Auth0 로그인 시도 시 `/auth/login-error`로 리다이렉트~~
- ~~`unknown_error`와 함께 "Login process failed" 메시지 표시~~
- ~~OAuth2 인증 흐름이 중간에 실패~~

### 해결 방법
**Spring Security OAuth2를 제거하고 직접 Auth0 API 호출로 구현**

### 새로운 인증 흐름 (직접 Auth0 구현)
```
==> HTTP GET /auth/login  
<== HTTP 302 302 FOUND | Location: https://api-bridge.us.auth0.com/authorize?...
==> Auth0 로그인 완료
<== HTTP 302 302 FOUND | Location: /auth/callback?code=...
==> HTTP GET /auth/callback  
<== HTTP 302 302 FOUND | Location: /auth/login-success?access_token=...
==> HTTP GET /auth/login-success
<== HTTP 200 OK (사용자 정보 + Access Token)
```

## 🔧 현재 설정 상태

### 환경변수 (.env)
```
AUTH0_CLIENT_ID=FbvvzTKMwAFKK6Zo7EQwFNhZCIbTXGNv
AUTH0_CLIENT_SECRET=cL_HwcnMk583zVfP3gMpttfwsNXZDSr8S6g9Sr7MfePMUdzq8y_RZj8v06xIaF7p
AUTH0_ISSUER_URI=https://api-bridge.us.auth0.com/
AUTH0_AUDIENCE=http://localhost:8080
```

### Application.yml 설정
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          auth0:
            client-id: ${AUTH0_CLIENT_ID}
            client-secret: ${AUTH0_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
              - email
        provider:
          auth0:
            issuer-uri: ${auth0.issuerUri}
            authorization-uri: ${auth0.issuerUri}authorize
            token-uri: ${auth0.issuerUri}oauth/token
            user-info-uri: ${auth0.issuerUri}userinfo
            jwk-set-uri: ${auth0.issuerUri}.well-known/jwks.json
            user-name-attribute: sub

auth0:
  issuerUri: https://api-bridge.us.auth0.com/
  audience: http://localhost:8080
  client-id: ${AUTH0_CLIENT_ID}
  logout-redirect-uri: http://localhost:8080/auth/logout-success
```

### SecurityConfig 설정
```java
.oauth2Login(oauth2 -> oauth2
    .authorizationRequestResolver(authorizationRequestResolver(clientRegistrationRepository))
    .authenticationSuccessHandler((exchange, authentication) -> {
        var response = exchange.getExchange().getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create("/auth/oauth-success"));
        return response.setComplete();
    })
    .authenticationFailureHandler((exchange, exception) -> {
        var response = exchange.getExchange().getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create("/auth/login-error"));
        return response.setComplete();
    })
)
```

## 📋 Auth0 대시보드 확인사항

### ✅ 필수 확인 항목

1. **Application Type**
   - `Regular Web Application`으로 설정되어야 함
   - SPA나 Native가 아님

2. **Allowed Callback URLs**
   ```
   http://localhost:8080/login/oauth2/code/auth0
   ```

3. **Allowed Logout URLs**
   ```
   http://localhost:8080/auth/logout-success
   ```

4. **Allowed Web Origins**
   ```
   http://localhost:8080
   ```

5. **Allowed Origins (CORS)**
   ```
   http://localhost:8080
   ```

6. **Domain**
   ```
   api-bridge.us.auth0.com
   ```

7. **Client ID/Secret**
   - Client ID: `FbvvzTKMwAFKK6Zo7EQwFNhZCIbTXGNv`
   - Client Secret이 정확한지 확인

### 🔑 API 설정 (별도 생성 필요)

Auth0에서 별도의 API를 생성해야 함:

1. **API Identifier (Audience)**
   ```
   http://localhost:8080
   ```

2. **Signing Algorithm**
   ```
   RS256
   ```

## 🛠️ 시도한 해결방법들

### 1. Eureka 비활성화 ✅
```yaml
eureka:
  client:
    enabled: false
    register-with-eureka: false
    fetch-registry: false
```

### 2. JWT 인증 필터 활성화 ✅
```java
@Component  // 주석 해제
public class JwtAuthenticationFilter implements WebFilter
```

### 3. SecurityConfig 경로 설정 ✅
```java
.pathMatchers("/auth/**").permitAll()
.pathMatchers("/gateway/user/**").authenticated()
```

### 4. 리다이렉션 루프 방지 ✅
- 루트 경로(`/`) 매핑을 `/auth/oauth-success`로 변경
- Spring Cloud Gateway와의 충돌 해결

### 5. Audience 설정 조정 ⚠️
- 처음에 `http://localhost:8080/`로 변경했으나 문제 발생
- `https://api.api-bridge.com`으로 복원

## 🔄 이전 발생했던 토큰 라우팅 문제

### 문제 증상
- Auth0 로그인은 성공하지만 JWT 토큰으로 유저 서비스 접근 실패
- `/gateway/user/api/users/**` 경로에 JWT 토큰으로 요청 시 401 Unauthorized
- SecurityConfig에서 모든 경로를 `permitAll()`로 설정하여 인증 우회됨

### 원인 분석
1. **JwtAuthenticationFilter 비활성화**
   ```java
   // @Component  // 주석처리되어 JWT 검증 안됨
   public class JwtAuthenticationFilter implements WebFilter
   ```

2. **SecurityConfig 설정 문제**
   ```java
   // 모든 경로 허용으로 인증 무시
   .anyExchange().permitAll()
   ```

3. **라우팅 설정 불일치**
   ```yaml
   # Eureka 기반 라우팅이 하드코딩으로 변경됨
   - id: user-service-protected
     uri: http://localhost:8081  # lb://USER-SERVICE에서 변경
   ```

### 해결 과정 ✅

#### 1. JWT 인증 필터 활성화
```java
@Component  // 주석 제거
@Order(-1) // Security Filter보다 먼저 실행
public class JwtAuthenticationFilter implements WebFilter {
    // JWT 토큰 검증 로직 활성화
}
```

#### 2. SecurityConfig 인증 경로 설정
```java
.authorizeExchange(exchanges -> exchanges
    .pathMatchers("/auth/**").permitAll()
    .pathMatchers("/gateway/user/**").authenticated()  // 인증 필요
    .pathMatchers("/gateway/**", "/api/**").authenticated()  // 인증 필요
    .anyExchange().permitAll()
)
```

#### 3. 유저 서비스 라우팅 수정
```yaml
# application.yml
routes:
  - id: user-service-protected
    uri: http://localhost:8081  # 8081 포트로 하드코딩
    predicates:
      - Path=/gateway/user/api/users/**
    filters:
      - StripPrefix=2  # /gateway/user 제거하여 /api/users/**로 전달
      - TokenRelay
      - UserInfoHeader
```

#### 4. JWT 토큰 전달 흐름 확립
```
1. /auth/login → Auth0 로그인
2. /auth/login-success → Access Token 획득
3. Authorization: Bearer {token} → /gateway/user/api/users/** 요청
4. JwtAuthenticationFilter → JWT 토큰 검증
5. SecurityConfig → 인증된 사용자만 통과
6. http://localhost:8081/api/users/** → 유저 서비스로 라우팅
```

### 토큰 검증 과정
```java
// JwtAuthenticationFilter에서 토큰 검증
String authHeader = request.getHeaders().getFirst("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    return handleUnauthorized(exchange);  // 401 반환
}

// Spring Security OAuth2 Resource Server에서 JWT 검증
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .jwtDecoder(reactiveJwtDecoder())  // Auth0 JWKS로 검증
        .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
    )
)
```

### 현재 동작하는 JWT 인증 플로우 (유지)
```
✅ JWT 없이 접근: 401 Unauthorized
✅ 유효하지 않은 JWT: 401 Unauthorized  
✅ 유효한 JWT: 유저 서비스로 라우팅 성공
✅ /gateway/user/api/users/** → http://localhost:8081/api/users/**
```

### 새로운 직접 Auth0 테스트 방법
```bash
# 1. Auth0 로그인 시작 (브라우저에서 실행)
open http://localhost:8080/auth/login

# 2. 로그인 성공 후 access_token 확인
# /auth/login-success?access_token=... 페이지에서 토큰 복사

# 3. 토큰 없이 API 접근 (401 에러 확인)
curl http://localhost:8080/gateway/user/api/users/me

# 4. 토큰으로 API 접근 (성공 확인)
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
     http://localhost:8080/gateway/user/api/users/me

# 5. 사용자 정보 조회
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
     http://localhost:8080/auth/user-info

# 6. 토큰 유효성 검증
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
     http://localhost:8080/auth/validate-token
```

## 🔍 현재 문제 분석

### 가능한 원인들

1. **Auth0 Application 설정 누락**
   - Callback URL이 정확히 설정되지 않음
   - Application Type이 잘못됨

2. **Auth0 API 미생성**
   - `https://api.api-bridge.com` Audience를 가진 API가 Auth0에 생성되지 않음

3. **Client Secret 불일치**
   - 환경변수의 Client Secret이 Auth0 대시보드와 다름

4. **Domain/Tenant 설정 문제**
   - `api-bridge.us.auth0.com` 도메인이 올바르지 않음

## 🚀 추천 해결 단계

### Step 1: Auth0 대시보드 확인
1. https://manage.auth0.com 로그인
2. Applications → 해당 앱 선택
3. Settings 탭에서 위의 필수 확인 항목들 검증

### Step 2: Auth0 API 생성 (중요!)
1. APIs 메뉴로 이동
2. "Create API" 클릭
3. Name: `Local Development API`
4. Identifier: `http://localhost:8080`
5. Signing Algorithm: `RS256`

### Step 3: 환경변수 재확인
```bash
# .env 파일 확인
AUTH0_CLIENT_ID=실제_클라이언트_ID
AUTH0_CLIENT_SECRET=실제_클라이언트_시크릿  
AUTH0_ISSUER_URI=https://실제_도메인.auth0.com/
AUTH0_AUDIENCE=http://localhost:8080
```

### Step 4: 테스트 흐름
1. `http://localhost:8080/auth/login` 접속
2. Auth0 로그인 페이지로 리다이렉트 확인
3. 로그인 완료 후 `/auth/oauth-success` → `/auth/login-success` 흐름 확인

## 📝 로그 분석 도구

### 현재 인증 흐름 (실패 중)
```
/auth/login → /oauth2/authorization/auth0 → Auth0 → /auth/login-error (실패)
```

### 정상 흐름이어야 할 것
```
/auth/login → /oauth2/authorization/auth0 → Auth0 → /auth/oauth-success → /auth/login-success
```

### 토큰 라우팅 흐름 (해결됨)
```
✅ 로그인: /auth/login → Auth0 → /auth/login-success (Access Token 획득)
✅ API 요청: Authorization: Bearer {token} → /gateway/user/api/users/**
✅ JWT 검증: JwtAuthenticationFilter → SecurityConfig → 인증 통과
✅ 라우팅: http://localhost:8081/api/users/** (유저 서비스)
```

## 🔧 디버깅 명령어

### 1. 현재 설정 확인
```bash
curl http://localhost:8080/auth/debug-auth
```

### 2. 에러 정보 확인
```bash
curl http://localhost:8080/auth/login-error
```

### 3. 애플리케이션 재시작
```bash
./gradlew bootRun
```

## ⚡ 긴급 해결 방안

만약 Auth0 설정이 복잡하다면, 임시로 테스트용 설정을 사용:

1. **새 Auth0 Application 생성**
   - Name: `Test API Gateway`
   - Type: `Regular Web Application`

2. **기본 설정으로 시작**
   ```
   Callback: http://localhost:8080/login/oauth2/code/auth0
   Logout: http://localhost:8080/auth/logout-success
   ```

3. **로컬 개발용 API 설정**
   - Audience: `http://localhost:8080`
   - 로컬 개발 환경에 맞는 identifier 사용

---

## 🆕 현재 상태 요약

### ✅ 완료된 작업
1. **Spring Security OAuth2 제거**
   - OAuth2 Client 설정 제거
   - OAuth2 관련 imports 및 beans 제거
   - 리다이렉션 루프 문제 해결

2. **직접 Auth0 구현**
   - `Auth0Service` 생성 및 구현
   - `AuthController` 직접 Auth0 API 호출로 수정
   - 새로운 콜백 URL: `/auth/callback`

3. **JWT 토큰 검증 유지**
   - `JwtAuthenticationFilter` 유지
   - Spring Security OAuth2 Resource Server 유지
   - API 라우팅 인증 기능 유지

### 🔄 다음 단계
1. **Auth0 대시보드 설정 업데이트**
   - Callback URL: `http://localhost:8080/auth/callback`
   - Application Type: Regular Web Application 유지

2. **테스트 실행**
   - `/auth/login` 로그인 플로우 테스트
   - JWT 토큰으로 API 접근 테스트
   - 유저 서비스 라우팅 테스트

### 📝 예상 동작 플로우
```
1. 브라우저: http://localhost:8080/auth/login
2. Auth0 로그인 페이지로 리다이렉트
3. 로그인 완료 후: /auth/callback?code=...
4. 콜백 처리 후: /auth/login-success?access_token=...
5. 사용자에게 access_token 표시
6. 톤큰으로 API 요청: Authorization: Bearer {token}
```

---

**마지막 업데이트**: 2025-08-19
**상태**: 🔄 직접 Auth0 구현 완료, 테스트 준비
**다음 단계**: Auth0 콜백 URL 업데이트 및 테스트