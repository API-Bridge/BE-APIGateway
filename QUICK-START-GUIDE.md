# API Gateway 빠른 시작 가이드 🚀

Redis와 Kafka가 활성화된 완전한 API Gateway를 빠르게 실행하는 방법입니다.

## ⚡ 빠른 실행 (5분 설정)

### 1단계: Docker Compose 실행
```bash
# 프로젝트 루트에서 실행
docker-compose up -d

# 서비스 상태 확인 (모든 서비스가 UP이 될 때까지 대기)
docker-compose ps
```

### 2단계: 서비스 준비 대기 (약 30-60초)
```bash
# 헬스체크 확인
curl http://localhost:8080/actuator/health

# 모든 서비스가 UP 상태인지 확인
# ✅ Redis (포트 6380)
# ✅ Kafka (포트 9093) 
# ✅ Kafka UI (포트 8081)
# ✅ API Gateway (포트 8080)
```

### 3단계: 기본 테스트
```bash
# 공개 엔드포인트 테스트
curl http://localhost:8080/public/health

# JWT 토큰 생성 (테스트용)
curl -X POST http://localhost:8080/api/test/generate-token \
  -H "Content-Type: application/json" \
  -d '{"sub": "test-user", "email": "test@example.com"}'

# 보호된 리소스 접근 (위에서 받은 토큰 사용)
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/users/profile
```

## 🔍 실행 상태 확인

### 모든 서비스 상태 확인
```bash
# Docker 컨테이너 상태
docker-compose ps

# API Gateway 헬스체크
curl http://localhost:8080/actuator/health | jq '.'

# Redis 연결 테스트
docker exec api-gateway-redis redis-cli ping

# Kafka 브로커 확인
docker exec api-gateway-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

### 웹 인터페이스 접속
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Kafka UI**: http://localhost:8081
- **API Gateway Health**: http://localhost:8080/actuator/health

## 📊 주요 기능 확인

### Rate Limiting 테스트
```bash
# Rate Limit 테스트 (5req/s 제한)
for i in {1..10}; do
  curl -H "Authorization: Bearer YOUR_TOKEN" \
    http://localhost:8080/gateway/ratelimit/test
  echo "Request $i"
  sleep 0.1
done
```

### Redis 데이터 확인
```bash
# Redis CLI 접속
docker exec -it api-gateway-redis redis-cli

# Rate Limit 키 확인
KEYS *rate*

# 차단 상태 확인
KEYS blocked:*
```

### Kafka 로그 확인
```bash
# 실시간 로그 스트림
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092

# 또는 Kafka UI 사용: http://localhost:8081
```

## 🛠️ 개발 환경 설정

### IntelliJ/VSCode에서 실행
1. Docker Compose로 인프라만 실행:
   ```bash
   # API Gateway 제외하고 실행
   docker-compose up -d redis kafka zookeeper kafka-ui
   ```

2. IDE에서 API Gateway 애플리케이션 실행:
   - Main Class: `APIGatewaySvcApplication`
   - Environment Variables: `.env` 파일 내용
   - Active Profile: `local`

### 환경변수 확인
```bash
# .env 파일 내용 확인
cat .env

# 중요한 설정들:
# - SPRING_REDIS_HOST=localhost
# - SPRING_REDIS_PORT=6380
# - KAFKA_BOOTSTRAP_SERVERS=localhost:9093
# - redis.enabled=true
# - kafka.enabled=true
```

## 📋 테스트 체크리스트

### ✅ 기본 기능
- [ ] API Gateway 시작 성공
- [ ] Redis 연결 성공
- [ ] Kafka 연결 성공
- [ ] Auth0 인증 작동
- [ ] Rate Limiting 작동
- [ ] 차단 시스템 작동
- [ ] 로깅 시스템 작동

### ✅ 고급 기능
- [ ] Circuit Breaker 작동
- [ ] 표준 응답 래핑
- [ ] 민감정보 마스킹
- [ ] 로그인 시도 추적
- [ ] Prometheus 메트릭

## 🚨 문제 해결

### 서비스가 시작되지 않는 경우
```bash
# 모든 컨테이너 중지 후 재시작
docker-compose down
docker-compose up -d

# 로그 확인
docker-compose logs -f api-gateway
```

### Redis 연결 실패
```bash
# Redis 상태 확인
docker logs api-gateway-redis

# 포트 확인
netstat -an | grep 6380
```

### Kafka 연결 실패
```bash
# Kafka 로그 확인
docker logs api-gateway-kafka

# Zookeeper 상태 확인
docker logs api-gateway-zookeeper
```

## 📚 상세 가이드

더 자세한 내용은 다음 문서들을 참고하세요:

- **전체 테스트**: `POSTMAN-TEST-GUIDE.md`
- **모니터링**: `REDIS-KAFKA-MONITORING-GUIDE.md`
- **아키텍처**: `GATEWAY-ARCHITECTURE.md`
- **기능 상세**: `FEATURES-GUIDE.md`

## 🎯 다음 단계

1. **Postman 컬렉션 임포트**: `postman/` 폴더의 파일들
2. **모니터링 설정**: Prometheus, Grafana 연동
3. **운영 환경 배포**: Kubernetes, AWS ECS 등
4. **보안 강화**: 실제 Auth0 설정, 시크릿 관리

---

축하합니다! 🎉 완전한 기능의 API Gateway가 실행 중입니다.
모든 기능(인증, Rate Limiting, 로깅, 모니터링)이 활성화되어 있습니다.