# 우리 서비스 Kafka 연동 시뮬레이션 테스트
Write-Host "=== API Gateway ↔ Kafka 연동 시뮬레이션 ===" -ForegroundColor Green
Write-Host ""

Write-Host "🔍 1. 현재 Kafka 상태 확인" -ForegroundColor Yellow
docker ps --filter "name=kafka" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Write-Host ""

Write-Host "📋 2. 사용 가능한 토픽 확인" -ForegroundColor Yellow
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
Write-Host ""

Write-Host "🚀 3. API Gateway가 보낼 실제 로그 시뮬레이션" -ForegroundColor Yellow

# 게이트웨이 로그 이벤트 시뮬레이션
$gatewayLog = @{
    eventType = "REQUEST"
    requestId = "req-$(Get-Random)"
    method = "GET"
    path = "/test/api"
    statusCode = 200
    responseTime = 45
    clientIp = "127.0.0.1"
    userAgent = "PostmanRuntime/7.32.0"
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
} | ConvertTo-Json -Compress

Write-Host "전송할 게이트웨이 로그:" -ForegroundColor Cyan
Write-Host $gatewayLog
echo $gatewayLog | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic logs.gateway
Write-Host "✅ 게이트웨이 로그 전송 완료!" -ForegroundColor Green
Write-Host ""

# 인증 이벤트 시뮬레이션
$authEvent = @{
    eventType = "LOGIN_SUCCESS"
    userId = "user-$(Get-Random)"
    clientIp = "192.168.1.100"
    userAgent = "Chrome/120.0.0.0"
    provider = "auth0"
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
} | ConvertTo-Json -Compress

Write-Host "🔐 4. 인증 이벤트 시뮬레이션" -ForegroundColor Yellow
Write-Host "전송할 인증 이벤트:" -ForegroundColor Cyan
Write-Host $authEvent
echo $authEvent | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic events.auth
Write-Host "✅ 인증 이벤트 전송 완료!" -ForegroundColor Green
Write-Host ""

# Rate Limit 이벤트 시뮬레이션
$rateLimitEvent = @{
    eventType = "RATE_LIMIT_EXCEEDED"
    clientKey = "ip:127.0.0.1"
    requestPath = "/api/users"
    currentRate = 25
    limitRate = 20
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
} | ConvertTo-Json -Compress

Write-Host "⚡ 5. Rate Limit 이벤트 시뮬레이션" -ForegroundColor Yellow
Write-Host "전송할 Rate Limit 이벤트:" -ForegroundColor Cyan
Write-Host $rateLimitEvent
echo $rateLimitEvent | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic events.ratelimit
Write-Host "✅ Rate Limit 이벤트 전송 완료!" -ForegroundColor Green
Write-Host ""

Write-Host "📥 6. 모든 토픽의 최신 메시지 확인" -ForegroundColor Yellow
Write-Host ""

Write-Host "[logs.gateway 토픽 - 최신 메시지]:" -ForegroundColor Magenta
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic logs.gateway --from-beginning --max-messages 1 --timeout-ms 3000 2>$null
Write-Host ""

Write-Host "[events.auth 토픽 - 최신 메시지]:" -ForegroundColor Magenta
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic events.auth --from-beginning --max-messages 1 --timeout-ms 3000 2>$null
Write-Host ""

Write-Host "[events.ratelimit 토픽 - 최신 메시지]:" -ForegroundColor Magenta
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic events.ratelimit --from-beginning --max-messages 1 --timeout-ms 3000 2>$null
Write-Host ""

Write-Host "🎉 Kafka 연동 시뮬레이션 완료!" -ForegroundColor Green
Write-Host "실제 API Gateway가 실행되면 이런 식으로 자동으로 로그가 Kafka에 전송됩니다!" -ForegroundColor White