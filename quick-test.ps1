# ============================================================================
# API Gateway 빠른 테스트 명령어 모음 (PowerShell)
# ============================================================================

Write-Host "🚀 API Gateway 빠른 테스트 명령어 모음" -ForegroundColor Green
Write-Host ""

$baseUrl = "http://localhost:8080"

Write-Host "📋 기본 테스트 명령어들:" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. 헬스체크 (Eureka 등록 확인):" -ForegroundColor Yellow
Write-Host "curl -X GET $baseUrl/actuator/health" -ForegroundColor White
Write-Host ""

Write-Host "2. 공개 엔드포인트 테스트:" -ForegroundColor Yellow
Write-Host "curl -X GET $baseUrl/test/public" -ForegroundColor White
Write-Host ""

Write-Host "3. 인증 필요 엔드포인트 (401 예상):" -ForegroundColor Yellow
Write-Host "curl -X GET $baseUrl/test/protected" -ForegroundColor White
Write-Host ""

Write-Host "4. CORS 프리플라이트 테스트:" -ForegroundColor Yellow
Write-Host "curl -X OPTIONS $baseUrl/api/users/me \\" -ForegroundColor White
Write-Host "  -H `"Origin: http://localhost:3000`" \\" -ForegroundColor White
Write-Host "  -H `"Access-Control-Request-Method: GET`" \\" -ForegroundColor White
Write-Host "  -H `"Access-Control-Request-Headers: Authorization,Content-Type`"" -ForegroundColor White
Write-Host ""

Write-Host "5. Auth0 로그인 URL 조회:" -ForegroundColor Yellow
Write-Host "curl -X GET $baseUrl/auth/login" -ForegroundColor White
Write-Host ""

Write-Host "6. Auth0 로그아웃 URL 조회:" -ForegroundColor Yellow
Write-Host "curl -X GET $baseUrl/auth/logout-url" -ForegroundColor White
Write-Host ""

Write-Host "📋 JWT 토큰 테스트 명령어들 (토큰 필요):" -ForegroundColor Cyan
Write-Host ""

Write-Host "JWT 토큰을 입력하세요 (Bearer 제외):" -ForegroundColor Yellow
$token = Read-Host "Access Token"

if ($token -and $token.Trim() -ne "") {
    $authHeader = "Authorization: Bearer $($token.Trim())"
    
    Write-Host ""
    Write-Host "7. 토큰 검증:" -ForegroundColor Yellow
    Write-Host "curl -X GET $baseUrl/auth/validate-token \\" -ForegroundColor White
    Write-Host "  -H `"$authHeader`"" -ForegroundColor White
    Write-Host ""
    
    Write-Host "8. 사용자 정보 조회:" -ForegroundColor Yellow
    Write-Host "curl -X GET $baseUrl/auth/user-info \\" -ForegroundColor White
    Write-Host "  -H `"$authHeader`"" -ForegroundColor White
    Write-Host ""
    
    Write-Host "9. Gateway 보호된 엔드포인트:" -ForegroundColor Yellow
    Write-Host "curl -X GET $baseUrl/test/protected \\" -ForegroundColor White
    Write-Host "  -H `"$authHeader`"" -ForegroundColor White
    Write-Host ""
    
    Write-Host "10. Gateway 헤더 확인:" -ForegroundColor Yellow
    Write-Host "curl -X GET $baseUrl/test/headers \\" -ForegroundColor White
    Write-Host "  -H `"$authHeader`"" -ForegroundColor White
    Write-Host ""
    
    Write-Host "11. User Service 연동 (TokenRelay 확인):" -ForegroundColor Yellow
    Write-Host "curl -X GET $baseUrl/api/users/me \\" -ForegroundColor White
    Write-Host "  -H `"$authHeader`"" -ForegroundColor White
    Write-Host ""
    
    Write-Host "12. User Service 헤더 전달 확인:" -ForegroundColor Yellow
    Write-Host "curl -X GET $baseUrl/api/users/test-headers \\" -ForegroundColor White
    Write-Host "  -H `"$authHeader`"" -ForegroundColor White
    Write-Host ""
    
    Write-Host "📋 실제 실행할 명령어들:" -ForegroundColor Cyan
    Write-Host ""
    
    Write-Host "원하는 테스트를 선택하세요 (1-12, 또는 'all' for 전체):" -ForegroundColor Yellow
    $choice = Read-Host "선택"
    
    switch ($choice) {
        "1" {
            Write-Host "헬스체크 실행 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/actuator/health"
        }
        "2" {
            Write-Host "공개 엔드포인트 테스트 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/test/public"
        }
        "3" {
            Write-Host "인증 필요 엔드포인트 테스트 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/test/protected"
        }
        "7" {
            Write-Host "토큰 검증 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/auth/validate-token" -H "$authHeader"
        }
        "8" {
            Write-Host "사용자 정보 조회 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/auth/user-info" -H "$authHeader"
        }
        "9" {
            Write-Host "보호된 엔드포인트 테스트 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/test/protected" -H "$authHeader"
        }
        "10" {
            Write-Host "헤더 확인 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/test/headers" -H "$authHeader"
        }
        "11" {
            Write-Host "User Service 연동 테스트 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/api/users/me" -H "$authHeader"
        }
        "12" {
            Write-Host "User Service 헤더 확인 중..." -ForegroundColor Green
            curl -X GET "$baseUrl/api/users/test-headers" -H "$authHeader"
        }
        "all" {
            Write-Host "전체 테스트 실행 중..." -ForegroundColor Green
            Write-Host ""
            
            Write-Host "=== 헬스체크 ===" -ForegroundColor Cyan
            curl -X GET "$baseUrl/actuator/health"
            Write-Host ""
            
            Write-Host "=== 공개 엔드포인트 ===" -ForegroundColor Cyan
            curl -X GET "$baseUrl/test/public"
            Write-Host ""
            
            Write-Host "=== 토큰 검증 ===" -ForegroundColor Cyan
            curl -X GET "$baseUrl/auth/validate-token" -H "$authHeader"
            Write-Host ""
            
            Write-Host "=== 보호된 엔드포인트 ===" -ForegroundColor Cyan
            curl -X GET "$baseUrl/test/protected" -H "$authHeader"
            Write-Host ""
            
            Write-Host "=== User Service 연동 ===" -ForegroundColor Cyan
            curl -X GET "$baseUrl/api/users/me" -H "$authHeader"
            Write-Host ""
        }
        default {
            Write-Host "올바른 선택이 아닙니다." -ForegroundColor Red
        }
    }
} else {
    Write-Host ""
    Write-Host "JWT 토큰 없이 기본 테스트만 실행:" -ForegroundColor Yellow
    Write-Host ""
    
    Write-Host "=== 헬스체크 ===" -ForegroundColor Cyan
    curl -X GET "$baseUrl/actuator/health"
    Write-Host ""
    
    Write-Host "=== 공개 엔드포인트 ===" -ForegroundColor Cyan
    curl -X GET "$baseUrl/test/public"
    Write-Host ""
    
    Write-Host "=== 인증 필요 엔드포인트 (401 예상) ===" -ForegroundColor Cyan
    curl -X GET "$baseUrl/test/protected"
    Write-Host ""
}

Write-Host ""
Write-Host "🎉 테스트 완료!" -ForegroundColor Green

