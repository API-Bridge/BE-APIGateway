# ============================================================================
# API Gateway 테스트 스크립트 (PowerShell)
# Spring Cloud Gateway + Auth0 + Eureka 연동 테스트
# ============================================================================

Write-Host "🚀 API Gateway 테스트 시작" -ForegroundColor Green
Write-Host "Base URL: http://localhost:8080" -ForegroundColor Yellow
Write-Host ""

# 기본 설정
$baseUrl = "http://localhost:8080"
$headers = @{
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}

# ============================================================================
# 1. 헬스체크 및 공개 엔드포인트 테스트
# ============================================================================

Write-Host "📋 1. 헬스체크 및 공개 엔드포인트 테스트" -ForegroundColor Cyan
Write-Host "=" * 50

Write-Host "1-1. Actuator Health Check (Eureka 등록 확인)" -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method GET -Headers $headers
    Write-Host "✅ Status: " -NoNewline -ForegroundColor Green
    Write-Host $response.status -ForegroundColor Yellow
    if ($response.components) {
        Write-Host "   Components:" -ForegroundColor Gray
        $response.components.PSObject.Properties | ForEach-Object {
            Write-Host "   - $($_.Name): $($_.Value.status)" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "1-2. Gateway Info" -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/actuator/info" -Method GET -Headers $headers
    Write-Host "✅ Gateway Info 조회 성공" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 3
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "1-3. 공개 테스트 엔드포인트" -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/test/public" -Method GET -Headers $headers
    Write-Host "✅ Public endpoint 접근 성공" -ForegroundColor Green
    Write-Host "   Message: $($response.message)" -ForegroundColor Gray
    Write-Host "   Authenticated: $($response.authenticated)" -ForegroundColor Gray
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ============================================================================
# 2. 인증 없이 보호된 엔드포인트 접근 (401 예상)
# ============================================================================

Write-Host "📋 2. 인증 없이 보호된 엔드포인트 접근 테스트" -ForegroundColor Cyan
Write-Host "=" * 50

$protectedEndpoints = @(
    "/test/protected",
    "/test/headers", 
    "/api/users/me",
    "/api/users/test-headers"
)

foreach ($endpoint in $protectedEndpoints) {
    Write-Host "2-$($protectedEndpoints.IndexOf($endpoint) + 1). GET $endpoint (토큰 없음)" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl$endpoint" -Method GET -Headers $headers
        Write-Host "⚠️  예상과 다름: 인증 없이 접근 성공" -ForegroundColor Yellow
        $response | ConvertTo-Json -Depth 2
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        
        switch ($statusCode) {
            401 { 
                Write-Host "✅ 예상대로 401 Unauthorized (인증 필요)" -ForegroundColor Green 
            }
            403 { 
                Write-Host "✅ 403 Forbidden (권한 부족)" -ForegroundColor Green 
            }
            406 { 
                Write-Host "⚠️  406 Not Acceptable - Accept 헤더 문제 재시도" -ForegroundColor Yellow
                try {
                    $webResponse = Invoke-WebRequest -Uri "$baseUrl$endpoint" -Method GET -Headers @{"Accept"="*/*"} -UseBasicParsing -ErrorAction Stop
                    Write-Host "   재시도 성공: $($webResponse.StatusCode)" -ForegroundColor Green
                } catch {
                    $retryStatusCode = [int]$_.Exception.Response.StatusCode
                    if ($retryStatusCode -eq 401) {
                        Write-Host "   ✅ 재시도 결과: 401 Unauthorized (정상)" -ForegroundColor Green
                    } elseif ($retryStatusCode -eq 403) {
                        Write-Host "   ✅ 재시도 결과: 403 Forbidden (정상)" -ForegroundColor Green
                    } else {
                        Write-Host "   ❌ 재시도 실패: $retryStatusCode" -ForegroundColor Red
                    }
                }
            }
            404 { 
                Write-Host "❌ 404 Not Found - 엔드포인트가 존재하지 않음" -ForegroundColor Red 
            }
            default { 
                Write-Host "❌ 예상과 다른 에러 ($statusCode): $($_.Exception.Message)" -ForegroundColor Red 
            }
        }
    }
    Write-Host ""
}

# ============================================================================
# 3. CORS 프리플라이트 테스트
# ============================================================================

Write-Host "📋 3. CORS 프리플라이트 테스트" -ForegroundColor Cyan
Write-Host "=" * 50

Write-Host "3-1. OPTIONS 요청 테스트" -ForegroundColor White
try {
    $corsHeaders = @{
        "Origin" = "http://localhost:3000"
        "Access-Control-Request-Method" = "GET"
        "Access-Control-Request-Headers" = "Authorization,Content-Type"
    }
    
    $response = Invoke-WebRequest -Uri "$baseUrl/api/users/me" -Method OPTIONS -Headers $corsHeaders
    Write-Host "✅ CORS Preflight 성공 (Status: $($response.StatusCode))" -ForegroundColor Green
    
    $corsResponseHeaders = $response.Headers
    if ($corsResponseHeaders["Access-Control-Allow-Origin"]) {
        Write-Host "   Allow-Origin: $($corsResponseHeaders['Access-Control-Allow-Origin'])" -ForegroundColor Gray
    }
    if ($corsResponseHeaders["Access-Control-Allow-Methods"]) {
        Write-Host "   Allow-Methods: $($corsResponseHeaders['Access-Control-Allow-Methods'])" -ForegroundColor Gray
    }
    if ($corsResponseHeaders["Access-Control-Allow-Headers"]) {
        Write-Host "   Allow-Headers: $($corsResponseHeaders['Access-Control-Allow-Headers'])" -ForegroundColor Gray
    }
} catch {
    Write-Host "❌ CORS Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ============================================================================
# 4. Auth0 로그인 관련 엔드포인트 테스트
# ============================================================================

Write-Host "📋 4. Auth0 로그인 관련 엔드포인트 테스트" -ForegroundColor Cyan
Write-Host "=" * 50

Write-Host "4-1. 로그인 시작 엔드포인트 (리다이렉트 테스트)" -ForegroundColor White
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/auth/login" -Method GET -MaximumRedirection 0 -ErrorAction SilentlyContinue
    if ($response.StatusCode -eq 302) {
        $redirectLocation = $response.Headers.Location
        Write-Host "✅ Login 리다이렉트 성공 (302)" -ForegroundColor Green
        Write-Host "   Redirect Location: $redirectLocation" -ForegroundColor Gray
        if ($redirectLocation -like "*oauth2/authorization/auth0*") {
            Write-Host "   ✅ Auth0 OAuth2 URL로 올바르게 리다이렉트" -ForegroundColor Green
        }
    } else {
        Write-Host "❌ 예상된 302 리다이렉트가 아님: $($response.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "4-2. 로그아웃 URL 조회" -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/auth/logout-url" -Method GET -Headers $headers
    Write-Host "✅ Logout URL 조회 성공" -ForegroundColor Green
    Write-Host "   Status: $($response.status)" -ForegroundColor Gray
    Write-Host "   Logout URL: $($response.logoutUrl)" -ForegroundColor Gray
} catch {
    Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "4-3. 로그인 성공 상태 확인 (OAuth2 세션 & Access Token)" -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/auth/login-success" -Method GET -Headers $headers -ContentType "application/json"
    if ($response.status -eq "success") {
        Write-Host "✅ OAuth2 로그인 성공! 완전한 사용자 정보 조회" -ForegroundColor Green
        Write-Host "   Status: $($response.status)" -ForegroundColor Gray
        Write-Host "   Auth Provider: $($response.auth_provider)" -ForegroundColor Gray
        
        # Access Token 정보 (가장 중요!)
        if ($response.access_token) {
            $tokenPreview = $response.access_token.Substring(0, [Math]::Min(50, $response.access_token.Length)) + "..."
            Write-Host "   🔑 Access Token: $tokenPreview" -ForegroundColor Yellow
            Write-Host "   Token Type: $($response.access_token_type)" -ForegroundColor Gray
            Write-Host "   Token Expires: $($response.access_token_expires_at)" -ForegroundColor Gray
            Write-Host "   Token Scopes: $($response.access_token_scopes -join ', ')" -ForegroundColor Gray
        }
        
        # 사용자 정보
        if ($response.user) {
            Write-Host "   👤 사용자 정보:" -ForegroundColor Cyan
            Write-Host "     - Auth0 ID: $($response.user.auth0_user_id)" -ForegroundColor DarkGray
            Write-Host "     - Subject: $($response.user.sub)" -ForegroundColor DarkGray
            Write-Host "     - Email: $($response.user.email)" -ForegroundColor DarkGray
            Write-Host "     - Email Verified: $($response.user.email_verified)" -ForegroundColor DarkGray
            Write-Host "     - Name: $($response.user.name)" -ForegroundColor DarkGray
            Write-Host "     - Picture: $($response.user.picture)" -ForegroundColor DarkGray
            if ($response.user.created_at) {
                Write-Host "     - Created: $($response.user.created_at)" -ForegroundColor DarkGray
            }
            if ($response.user.last_login) {
                Write-Host "     - Last Login: $($response.user.last_login)" -ForegroundColor DarkGray
            }
            if ($response.user.logins_count) {
                Write-Host "     - Login Count: $($response.user.logins_count)" -ForegroundColor DarkGray
            }
            if ($response.user.permissions) {
                Write-Host "     - Permissions: $($response.user.permissions -join ', ')" -ForegroundColor DarkGray
            }
            if ($response.user.roles) {
                Write-Host "     - Roles: $($response.user.roles -join ', ')" -ForegroundColor DarkGray
            }
        }
        
        # 다음 단계 안내
        if ($response.next_steps) {
            Write-Host "   🚀 다음 단계:" -ForegroundColor Green
            Write-Host "     $($response.next_steps.message)" -ForegroundColor DarkGray
        }
        
    } elseif ($response.status -eq "not_logged_in") {
        Write-Host "⚠️  OAuth2 로그인이 필요합니다" -ForegroundColor Yellow
        Write-Host "   브라우저에서 $baseUrl/auth/login 접속하여 Auth0 로그인 완료 후 재시도" -ForegroundColor Gray
    } else {
        Write-Host "❌ 예상치 못한 상태: $($response.status)" -ForegroundColor Red
        Write-Host "   Message: $($response.message)" -ForegroundColor Gray
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 406) {
        Write-Host "⚠️  406 오류 - Accept 헤더 문제, 다른 방법으로 재시도" -ForegroundColor Yellow
        try {
            $webResponse = Invoke-WebRequest -Uri "$baseUrl/auth/login-success" -Method GET -Headers @{"Accept"="application/json"} -UseBasicParsing
            $jsonResponse = $webResponse.Content | ConvertFrom-Json
            Write-Host "✅ 재시도 성공 - Status: $($jsonResponse.status)" -ForegroundColor Green
        } catch {
            Write-Host "❌ 재시도도 실패: $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# ============================================================================
# 5. 유효한 JWT 토큰으로 보호된 엔드포인트 테스트
# ============================================================================

Write-Host "📋 5. JWT 토큰 입력 및 보호된 엔드포인트 테스트" -ForegroundColor Cyan
Write-Host "=" * 50

Write-Host "Auth0에서 발급받은 Access Token을 입력하세요:" -ForegroundColor Yellow
Write-Host "(토큰이 없으면 Enter를 눌러 건너뛰기)" -ForegroundColor Gray
$accessToken = Read-Host "Access Token"

if ($accessToken -and $accessToken.Trim() -ne "") {
    $authHeaders = $headers.Clone()
    $authHeaders["Authorization"] = "Bearer $($accessToken.Trim())"
    
    Write-Host ""
    Write-Host "5-1. 토큰 검증 상태 확인" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/auth/validate-token" -Method GET -Headers $authHeaders
        Write-Host "✅ 토큰 검증 성공" -ForegroundColor Green
        Write-Host "   Valid: $($response.valid)" -ForegroundColor Gray
        Write-Host "   Principal: $($response.principal)" -ForegroundColor Gray
    } catch {
        Write-Host "❌ 토큰 검증 실패: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host ""
    
    Write-Host "5-2. 사용자 정보 조회 (상세)" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/auth/user-info" -Method GET -Headers $authHeaders
        Write-Host "✅ 사용자 정보 조회 성공" -ForegroundColor Green
        Write-Host "   Auth Type: $($response.auth_type)" -ForegroundColor Gray
        Write-Host "   Principal: $($response.principal)" -ForegroundColor Gray
        Write-Host "   Authorities: $($response.authorities -join ', ')" -ForegroundColor Gray
        
        if ($response.user) {
            Write-Host "   User Info:" -ForegroundColor Gray
            Write-Host "     - Auth0 ID: $($response.user.sub)" -ForegroundColor DarkGray
            Write-Host "     - Email: $($response.user.email)" -ForegroundColor DarkGray
            Write-Host "     - Name: $($response.user.name)" -ForegroundColor DarkGray
            Write-Host "     - Nickname: $($response.user.nickname)" -ForegroundColor DarkGray
            Write-Host "     - Picture: $($response.user.picture)" -ForegroundColor DarkGray
            if ($response.user.permissions) {
                Write-Host "     - Permissions: $($response.user.permissions -join ', ')" -ForegroundColor DarkGray
            }
            if ($response.user.roles) {
                Write-Host "     - Roles: $($response.user.roles -join ', ')" -ForegroundColor DarkGray
            }
            if ($response.user.created_at) {
                Write-Host "     - Created: $($response.user.created_at)" -ForegroundColor DarkGray
            }
            if ($response.user.last_login) {
                Write-Host "     - Last Login: $($response.user.last_login)" -ForegroundColor DarkGray
            }
            if ($response.user.logins_count) {
                Write-Host "     - Login Count: $($response.user.logins_count)" -ForegroundColor DarkGray
            }
        }
        
        if ($response.jwt) {
            Write-Host "   JWT Info:" -ForegroundColor Gray
            Write-Host "     - Subject: $($response.jwt.subject)" -ForegroundColor DarkGray
            Write-Host "     - Issuer: $($response.jwt.issuer)" -ForegroundColor DarkGray
            Write-Host "     - Audience: $($response.jwt.audience -join ', ')" -ForegroundColor DarkGray
            Write-Host "     - Expires: $($response.jwt.expires_at)" -ForegroundColor DarkGray
        }
        
        if ($response.id_token_info) {
            Write-Host "   ID Token Info:" -ForegroundColor Gray
            Write-Host "     - Issuer: $($response.id_token_info.issuer)" -ForegroundColor DarkGray
            Write-Host "     - Expires: $($response.id_token_info.expires_at)" -ForegroundColor DarkGray
        }
    } catch {
        Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host ""
    
    Write-Host "5-3. Gateway 테스트 엔드포인트 (보호됨)" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/test/protected" -Method GET -Headers $authHeaders
        Write-Host "✅ Protected endpoint 접근 성공" -ForegroundColor Green
        Write-Host "   Message: $($response.message)" -ForegroundColor Gray
        Write-Host "   JWT Subject: $($response.jwt.subject)" -ForegroundColor Gray
    } catch {
        Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host ""
    
    Write-Host "5-4. 헤더 전달 확인" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/test/headers" -Method GET -Headers $authHeaders
        Write-Host "✅ Headers 확인 성공" -ForegroundColor Green
        Write-Host "   Has Authorization: $($response.hasAuthorization)" -ForegroundColor Gray
        Write-Host "   Has X-Gateway: $($response.hasXGateway)" -ForegroundColor Gray
        Write-Host "   JWT Present: $($response.jwtPresent)" -ForegroundColor Gray
    } catch {
        Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host ""
    
    Write-Host "5-5. User Service 연동 테스트 (TokenRelay 확인)" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/users/me" -Method GET -Headers $authHeaders
        Write-Host "✅ User Service 연동 성공" -ForegroundColor Green
        Write-Host "   Service: $($response.service)" -ForegroundColor Gray
        Write-Host "   Principal: $($response.principal)" -ForegroundColor Gray
        Write-Host "   Token Relay Success: $($response.jwt.tokenRelaySuccess)" -ForegroundColor Gray
        Write-Host "   JWT Subject: $($response.jwt.subject)" -ForegroundColor Gray
    } catch {
        if ($_.Exception.Response.StatusCode -eq 401) {
            Write-Host "❌ User Service 인증 실패 - TokenRelay 문제 가능성" -ForegroundColor Red
        } elseif ($_.Exception.Response.StatusCode -eq 503 -or $_.Exception.Message -like "*Connection refused*") {
            Write-Host "⚠️  User Service가 실행되지 않음" -ForegroundColor Yellow
        } else {
            Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    Write-Host ""
    
    Write-Host "5-6. User Service 헤더 전달 확인" -ForegroundColor White
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/users/test-headers" -Method GET -Headers $authHeaders
        Write-Host "✅ User Service 헤더 확인 성공" -ForegroundColor Green
        Write-Host "   Service: $($response.service)" -ForegroundColor Gray
        Write-Host "   Has Authorization: $($response.hasAuthorization)" -ForegroundColor Gray
        Write-Host "   Has X-Gateway: $($response.hasXGateway)" -ForegroundColor Gray
        Write-Host "   JWT Decoded: $($response.jwtDecoded)" -ForegroundColor Gray
        Write-Host "   Authorization Header: $($response.authorizationHeader.Substring(0, [Math]::Min(50, $response.authorizationHeader.Length)))..." -ForegroundColor Gray
    } catch {
        if ($_.Exception.Response.StatusCode -eq 503 -or $_.Exception.Message -like "*Connection refused*") {
            Write-Host "⚠️  User Service가 실행되지 않음" -ForegroundColor Yellow
        } else {
            Write-Host "❌ Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    Write-Host ""
    
} else {
    Write-Host "⏭️  JWT 토큰 테스트 건너뛰기" -ForegroundColor Yellow
    Write-Host ""
}

# ============================================================================
# 6. Gateway 라우트 정보 확인
# ============================================================================

Write-Host "📋 6. Gateway 라우트 정보 확인" -ForegroundColor Cyan
Write-Host "=" * 50

Write-Host "6-1. 현재 활성화된 라우트 목록" -ForegroundColor White
try {
    # 먼저 Gateway Actuator 엔드포인트 시도
    $response = Invoke-RestMethod -Uri "$baseUrl/actuator/gateway/routes" -Method GET -Headers $headers
    Write-Host "✅ Gateway 라우트 정보 조회 성공" -ForegroundColor Green
    
    if ($response -and $response.Count -gt 0) {
        foreach ($route in $response) {
            Write-Host "   Route ID: $($route.route_id)" -ForegroundColor Gray
            Write-Host "   URI: $($route.uri)" -ForegroundColor Gray
            if ($route.predicates) {
                Write-Host "   Predicates: $($route.predicates -join ', ')" -ForegroundColor Gray
            }
            if ($route.filters) {
                Write-Host "   Filters: $($route.filters -join ', ')" -ForegroundColor Gray
            }
            Write-Host "   ---" -ForegroundColor DarkGray
        }
    } else {
        Write-Host "   라우트 정보가 비어있습니다" -ForegroundColor Yellow
    }
} catch {
    $statusCode = $null
    if ($_.Exception.Response) {
        $statusCode = [int]$_.Exception.Response.StatusCode
    }
    
    if ($statusCode -eq 404) {
        Write-Host "⚠️  Gateway Actuator 엔드포인트가 비활성화됨" -ForegroundColor Yellow
        Write-Host "   application.yml에서 management.endpoints.web.exposure.include에" -ForegroundColor Gray
        Write-Host "   'gateway' 추가하면 활성화 가능" -ForegroundColor Gray
        
        # 대안으로 일반적인 라우트 정보 표시
        Write-Host "   📋 설정된 라우트 (예상):" -ForegroundColor Cyan
        Write-Host "     - user-service-protected: /api/users/**" -ForegroundColor DarkGray
        Write-Host "     - eureka-discovery routes" -ForegroundColor DarkGray
        Write-Host "     - rate-limit-test: /gateway/ratelimit/**" -ForegroundColor DarkGray
    } elseif ($statusCode -eq 406) {
        Write-Host "⚠️  406 Accept 헤더 문제 - 재시도" -ForegroundColor Yellow
        try {
            $webResponse = Invoke-WebRequest -Uri "$baseUrl/actuator/gateway/routes" -Method GET -Headers @{"Accept"="*/*"} -UseBasicParsing
            Write-Host "✅ 재시도 성공: Gateway 라우트 조회됨" -ForegroundColor Green
        } catch {
            Write-Host "❌ 재시도도 실패: $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "❌ Error ($statusCode): $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

# ============================================================================
# 7. 서비스 디스커버리 확인 (Eureka)
# ============================================================================

Write-Host "📋 7. Eureka 서비스 디스커버리 확인" -ForegroundColor Cyan
Write-Host "=" * 50

Write-Host "7-1. Eureka에 등록된 서비스 확인" -ForegroundColor White
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Method GET -Headers @{"Accept" = "application/json"}
    Write-Host "✅ Eureka 서비스 목록 조회 성공" -ForegroundColor Green
    
    if ($response.applications -and $response.applications.application) {
        foreach ($app in $response.applications.application) {
            $appName = if ($app.name) { $app.name } else { "Unknown" }
            $instanceCount = if ($app.instance) { $app.instance.Count } else { 0 }
            Write-Host "   서비스: $appName (인스턴스: $instanceCount개)" -ForegroundColor Gray
            
            if ($app.instance) {
                foreach ($instance in $app.instance) {
                    $status = if ($instance.status) { $instance.status } else { "UNKNOWN" }
                    $hostPort = if ($instance.hostName -and $instance.port) { "$($instance.hostName):$($instance.port.'$')" } else { "Unknown" }
                    Write-Host "     - $hostPort ($status)" -ForegroundColor DarkGray
                }
            }
        }
    } else {
        Write-Host "   등록된 서비스가 없습니다." -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Eureka 서버에 연결할 수 없습니다: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Eureka 서버가 실행 중인지 확인하세요 (http://localhost:8761)" -ForegroundColor Yellow
}
Write-Host ""

# ============================================================================
# 테스트 완료
# ============================================================================

Write-Host "🎉 API Gateway 테스트 완료!" -ForegroundColor Green
Write-Host ""
Write-Host "다음 단계:" -ForegroundColor Yellow
Write-Host "1. Auth0에서 Access Token을 발급받아 JWT 토큰 테스트 수행" -ForegroundColor Gray
Write-Host "2. User Service를 실행하여 마이크로서비스 연동 확인" -ForegroundColor Gray
Write-Host "3. Eureka 서버 실행 상태 확인 (http://localhost:8761)" -ForegroundColor Gray
Write-Host ""
Write-Host "스모크 테스트 체크리스트:" -ForegroundColor Yellow
Write-Host "✅ /actuator/health → 200 (Eureka 헬스체크)" -ForegroundColor Gray
Write-Host "✅ /test/public → 200 (비인증 접근)" -ForegroundColor Gray
Write-Host "✅ /test/protected (토큰 없음) → 401" -ForegroundColor Gray
Write-Host "🔄 /test/protected (유효 토큰) → 200 (JWT 토큰 필요)" -ForegroundColor Gray
Write-Host "🔄 /api/users/me → User Service 연동 확인" -ForegroundColor Gray
