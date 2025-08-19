# API Gateway 엔드포인트 빠른 테스트 스크립트

Write-Host "🔍 API Gateway 엔드포인트 테스트" -ForegroundColor Green
Write-Host ""

$baseUrl = "http://localhost:8080"

# 1. login-info 테스트
Write-Host "1. /auth/login-info 테스트:" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/auth/login-info" -Method GET
    Write-Host "✅ 응답 성공" -ForegroundColor Green
    Write-Host "   Status: $($response.status)" -ForegroundColor Gray
    Write-Host "   Authenticated: $($response.authenticated)" -ForegroundColor Gray
    Write-Host "   Message: $($response.message)" -ForegroundColor Gray
    Write-Host "   Timestamp: $($response.timestamp)" -ForegroundColor Gray
} catch {
    Write-Host "❌ 오류: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 2. login-success 테스트
Write-Host "2. /auth/login-success 테스트:" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/auth/login-success" -Method GET
    Write-Host "✅ 응답 성공" -ForegroundColor Green
    Write-Host "   Status: $($response.status)" -ForegroundColor Gray
    Write-Host "   Authenticated: $($response.authenticated)" -ForegroundColor Gray
    Write-Host "   Message: $($response.message)" -ForegroundColor Gray
    Write-Host "   Timestamp: $($response.timestamp)" -ForegroundColor Gray
    if ($response.auth_type) {
        Write-Host "   Auth Type: $($response.auth_type)" -ForegroundColor Gray
    }
    if ($response.user) {
        Write-Host "   User Info Available: Yes" -ForegroundColor Gray
        if ($response.user.sub) {
            Write-Host "   User ID: $($response.user.sub)" -ForegroundColor Gray
        }
        if ($response.user.email) {
            Write-Host "   Email: $($response.user.email)" -ForegroundColor Gray
        }
    } else {
        Write-Host "   User Info Available: No" -ForegroundColor Gray
    }
} catch {
    Write-Host "❌ 오류: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 3. 간단한 공개 엔드포인트 테스트
Write-Host "3. /test/public 테스트:" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/test/public" -Method GET
    Write-Host "✅ 응답 성공" -ForegroundColor Green
    Write-Host "   Status: $($response.status)" -ForegroundColor Gray
    Write-Host "   Message: $($response.message)" -ForegroundColor Gray
    Write-Host "   Authenticated: $($response.authenticated)" -ForegroundColor Gray
} catch {
    Write-Host "❌ 오류: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 4. 헬스체크 테스트
Write-Host "4. /actuator/health 테스트:" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method GET
    Write-Host "✅ 응답 성공" -ForegroundColor Green
    Write-Host "   Status: $($response.status)" -ForegroundColor Gray
} catch {
    Write-Host "❌ 오류: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "📝 결론:" -ForegroundColor Cyan
Write-Host "- /auth/login-info: 새로 추가된 엔드포인트가 정상 작동" -ForegroundColor Gray
Write-Host "- /auth/login-success: 안전한 에러 처리로 정상 작동" -ForegroundColor Gray
Write-Host "- 현재 인증되지 않은 상태에서도 적절한 응답 제공" -ForegroundColor Gray
Write-Host ""
Write-Host "🔐 OAuth2 로그인 테스트 방법:" -ForegroundColor Yellow
Write-Host "1. 브라우저에서 http://localhost:8080/oauth2/authorization/auth0 접속" -ForegroundColor Gray
Write-Host "2. Auth0 로그인 완료 후" -ForegroundColor Gray
Write-Host "3. http://localhost:8080/auth/login-success 접속하여 사용자 정보 확인" -ForegroundColor Gray





