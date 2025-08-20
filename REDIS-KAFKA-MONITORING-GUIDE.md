# Redis & Kafka 접속 및 모니터링 가이드 🔍

Redis와 Kafka 서비스에 접속하여 상태를 확인하고 모니터링하는 방법을 상세히 설명합니다.

## 🔴 Redis 접속 및 모니터링

### Docker 환경에서 Redis 접속

#### 1. Redis CLI 접속 방법
```bash
# 방법 1: Docker 컨테이너 내부 접속
docker exec -it api-gateway-redis redis-cli

# 방법 2: 로컬에서 포트로 직접 접속 (Redis CLI 설치 필요)
redis-cli -h localhost -p 6380

# 방법 3: Docker 명령어로 일회성 접속
docker exec -it api-gateway-redis redis-cli -h localhost -p 6379
```

#### 2. Redis 연결 상태 확인
```bash
# 연결 테스트
redis-cli -h localhost -p 6380 ping
# 응답: PONG

# 서버 정보 확인
redis-cli -h localhost -p 6380 info server
```

### Redis 데이터 조회 및 분석

#### Rate Limiting 데이터 확인
```redis
# Redis CLI 접속 후 실행

# 1. 모든 Rate Limit 관련 키 조회
KEYS *rate*
KEYS request_rate_limiter*

# 2. 특정 사용자의 Rate Limit 상태
GET "request_rate_limiter.user:auth0|123456.tokens"
GET "request_rate_limiter.user:auth0|123456.timestamp"

# 3. IP 기반 Rate Limit 상태
GET "request_rate_limiter.ip:127.0.0.1.tokens"

# 4. TTL(만료시간) 확인
TTL "request_rate_limiter.user:auth0|123456.tokens"

# 5. 모든 사용자의 Rate Limit 상태 조회
KEYS request_rate_limiter.user:*
```

#### 차단 시스템 데이터 확인
```redis
# 1. 모든 차단 관련 키 조회
KEYS blocked:*

# 2. 사용자 차단 상태
GET "blocked:user:auth0|123456"
TTL "blocked:user:auth0|123456"

# 3. IP 차단 상태
GET "blocked:ip:192.168.1.100"
TTL "blocked:ip:192.168.1.100"

# 4. API 키 차단 상태
GET "blocked:key:api_key_12345"

# 5. 차단 사유 확인 (값에 저장됨)
GET "blocked:user:auth0|123456"
# 예시 응답: "로그인 5회 실패 (IP: 192.168.1.100)"
```

#### 로그인 시도 추적 데이터 확인
```redis
# 1. 로그인 시도 관련 키 조회
KEYS login_attempts:*

# 2. 사용자별 로그인 시도 횟수
GET "login_attempts:auth0|123456"
TTL "login_attempts:auth0|123456"

# 3. IP별 로그인 시도 횟수
GET "login_attempts:ip:192.168.1.100"

# 4. 모든 로그인 시도 통계
KEYS login_attempts:*
```

### Redis 성능 모니터링

#### 메모리 사용량 확인
```redis
# 메모리 사용 정보
INFO memory

# 키별 메모리 사용량 (Redis 4.0+)
MEMORY USAGE "request_rate_limiter.user:auth0|123456.tokens"

# 데이터베이스 크기 정보
INFO keyspace
```

#### 실시간 모니터링
```bash
# 실시간 Redis 모니터링
docker exec -it api-gateway-redis redis-cli monitor

# 특정 패턴 키 실시간 추적
docker exec -it api-gateway-redis redis-cli --latency

# Redis 통계 정보 실시간 확인
docker exec -it api-gateway-redis redis-cli --stat
```

#### Redis 성능 메트릭
```redis
# 연결 정보
INFO clients

# 명령어 통계
INFO commandstats

# 복제 정보
INFO replication

# 서버 통계
INFO stats
```

---

## 🟡 Kafka 접속 및 모니터링

### Kafka 관리 인터페이스 접속

#### 1. Kafka UI 웹 인터페이스 (권장)
```
URL: http://localhost:8081
```

**주요 기능**:
- 토픽 목록 및 상세 정보
- 메시지 실시간 조회
- 컨슈머 그룹 모니터링
- 브로커 상태 확인

#### 2. Kafka CLI 도구 사용
```bash
# Kafka 컨테이너 내부 접속
docker exec -it api-gateway-kafka bash

# 또는 직접 명령어 실행
docker exec api-gateway-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Kafka 토픽 및 메시지 확인

#### 토픽 관리
```bash
# 1. 토픽 목록 조회
docker exec api-gateway-kafka kafka-topics --list --bootstrap-server localhost:9092

# 2. 토픽 상세 정보 확인
docker exec api-gateway-kafka kafka-topics --describe --topic logs.gateway --bootstrap-server localhost:9092

# 3. 토픽 생성 (필요시)
docker exec api-gateway-kafka kafka-topics --create \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# 4. 토픽 삭제 (주의!)
docker exec api-gateway-kafka kafka-topics --delete --topic logs.gateway --bootstrap-server localhost:9092
```

#### 메시지 실시간 모니터링
```bash
# 1. 실시간 메시지 스트림 확인
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 \
  --from-beginning

# 2. 최신 메시지만 확인
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092

# 3. 특정 개수 메시지만 확인
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 \
  --max-messages 10

# 4. JSON 형태로 파싱하여 확인 (jq 설치 필요)
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 \
  --max-messages 5 | jq '.'
```

### Gateway 로그 메시지 분석

#### 로그 메시지 유형별 확인
```bash
# 1. 요청 시작 이벤트만 필터링
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 | grep "REQUEST_START"

# 2. 에러 이벤트만 필터링
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 | grep "REQUEST_ERROR"

# 3. 특정 사용자 요청만 필터링
docker exec api-gateway-kafka kafka-console-consumer \
  --topic logs.gateway \
  --bootstrap-server localhost:9092 | grep "auth0|123456"
```

#### 로그 메시지 구조 분석
Gateway에서 전송되는 로그 메시지 형식:

```json
{
  "eventType": "REQUEST_START",
  "requestId": "12345678-1234-1234-1234-123456789012",
  "timestamp": "2024-01-01T12:00:00.000Z",
  "method": "GET",
  "url": "/api/users/profile?userId=123",
  "clientIp": "172.19.0.1",
  "userId": "auth0|123456",
  "userAgent": "PostmanRuntime/7.28.4",
  "referer": null,
  "routeId": "user-service-local",
  "publicApiName": "Users API",
  "headers": {
    "authorization": "Bearer eyJ0***",
    "user-agent": "PostmanRuntime/7.28.4",
    "content-type": "application/json",
    "x-forwarded-for": "127.0.0.1"
  }
}
```

### Kafka 성능 모니터링

#### 컨슈머 그룹 모니터링
```bash
# 1. 컨슈머 그룹 목록 확인
docker exec api-gateway-kafka kafka-consumer-groups --list --bootstrap-server localhost:9092

# 2. 특정 컨슈머 그룹 상세 정보
docker exec api-gateway-kafka kafka-consumer-groups \
  --describe \
  --group gateway-consumer-group \
  --bootstrap-server localhost:9092

# 3. 컨슈머 지연(lag) 확인
docker exec api-gateway-kafka kafka-consumer-groups \
  --describe \
  --group gateway-consumer-group \
  --bootstrap-server localhost:9092 \
  --state
```

#### 브로커 상태 확인
```bash
# 1. 브로커 API 버전 확인
docker exec api-gateway-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# 2. 브로커 설정 확인
docker exec api-gateway-kafka kafka-configs \
  --describe \
  --entity-type brokers \
  --entity-name 1 \
  --bootstrap-server localhost:9092

# 3. 로그 세그먼트 확인
docker exec api-gateway-kafka kafka-log-dirs \
  --describe \
  --bootstrap-server localhost:9092 \
  --json
```

---

## 📊 통합 모니터링 스크립트

### Redis & Kafka 상태 체크 스크립트
```bash
#!/bin/bash
# monitoring-check.sh

echo "=== API Gateway Infrastructure Status ==="
echo

# Redis 상태 확인
echo "🔴 Redis Status:"
if docker exec api-gateway-redis redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis is UP (Port 6380)"
    echo "   Memory Usage: $(docker exec api-gateway-redis redis-cli info memory | grep used_memory_human)"
    echo "   Connected Clients: $(docker exec api-gateway-redis redis-cli info clients | grep connected_clients)"
    echo "   Total Keys: $(docker exec api-gateway-redis redis-cli dbsize)"
else
    echo "❌ Redis is DOWN"
fi
echo

# Kafka 상태 확인
echo "🟡 Kafka Status:"
if docker exec api-gateway-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then
    echo "✅ Kafka is UP (Port 9093)"
    echo "   Topics: $(docker exec api-gateway-kafka kafka-topics --list --bootstrap-server localhost:9092 | wc -l)"
    echo "   Consumer Groups: $(docker exec api-gateway-kafka kafka-consumer-groups --list --bootstrap-server localhost:9092 | wc -l)"
else
    echo "❌ Kafka is DOWN"
fi
echo

# API Gateway 상태 확인
echo "🚀 API Gateway Status:"
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ API Gateway is UP (Port 8080)"
    echo "   Health Status: $(curl -s http://localhost:8080/actuator/health | jq -r '.status')"
else
    echo "❌ API Gateway is DOWN"
fi
echo

echo "=== Quick Commands ==="
echo "Redis CLI: docker exec -it api-gateway-redis redis-cli"
echo "Kafka UI: http://localhost:8081"
echo "Kafka Logs: docker exec api-gateway-kafka kafka-console-consumer --topic logs.gateway --bootstrap-server localhost:9092"
echo "Gateway Health: curl http://localhost:8080/actuator/health"
```

### 실시간 모니터링 대시보드 (터미널)
```bash
#!/bin/bash
# realtime-monitor.sh

# 터미널을 여러 패널로 분할하여 실시간 모니터링
# tmux 또는 screen 사용 권장

# 패널 1: Redis 실시간 모니터링
watch -n 1 'docker exec api-gateway-redis redis-cli info stats | grep -E "instantaneous_ops_per_sec|connected_clients|used_memory_human"'

# 패널 2: Kafka 메시지 스트림
docker exec api-gateway-kafka kafka-console-consumer --topic logs.gateway --bootstrap-server localhost:9092

# 패널 3: API Gateway 로그
docker-compose logs -f api-gateway

# 패널 4: 시스템 리소스
watch -n 1 'docker stats api-gateway-redis api-gateway-kafka api-gateway-app --no-stream'
```

---

## 🚨 문제 해결 가이드

### 일반적인 문제들

#### Redis 연결 문제
```bash
# 1. Redis 컨테이너 상태 확인
docker ps | grep redis

# 2. Redis 로그 확인
docker logs api-gateway-redis

# 3. 포트 바인딩 확인
netstat -tulpn | grep 6380

# 4. Redis 설정 확인
docker exec api-gateway-redis redis-cli config get "*"
```

#### Kafka 연결 문제
```bash
# 1. Kafka 컨테이너 상태 확인
docker ps | grep kafka

# 2. Zookeeper 연결 확인
docker exec api-gateway-kafka kafka-topics --list --bootstrap-server localhost:9092

# 3. 로그 확인
docker logs api-gateway-kafka
docker logs api-gateway-zookeeper

# 4. 토픽 생성 문제 해결
docker exec api-gateway-kafka kafka-topics --create --topic logs.gateway --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

#### 성능 문제
```bash
# Redis 메모리 사용량 확인
docker exec api-gateway-redis redis-cli memory usage-sample

# Kafka 디스크 사용량 확인
docker exec api-gateway-kafka df -h

# 컨테이너 리소스 사용량 확인
docker stats --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"
```

### 로그 수집 및 분석

#### 문제 발생 시 로그 수집
```bash
# 전체 서비스 로그 수집
mkdir -p logs/$(date +%Y%m%d_%H%M%S)
docker-compose logs --no-color > logs/$(date +%Y%m%d_%H%M%S)/all-services.log
docker logs api-gateway-redis > logs/$(date +%Y%m%d_%H%M%S)/redis.log
docker logs api-gateway-kafka > logs/$(date +%Y%m%d_%H%M%S)/kafka.log
docker logs api-gateway-app > logs/$(date +%Y%m%d_%H%M%S)/gateway.log

# Redis 상태 덤프
docker exec api-gateway-redis redis-cli info all > logs/$(date +%Y%m%d_%H%M%S)/redis-info.txt
docker exec api-gateway-redis redis-cli config get "*" > logs/$(date +%Y%m%d_%H%M%S)/redis-config.txt

# Kafka 상태 덤프
docker exec api-gateway-kafka kafka-topics --describe --bootstrap-server localhost:9092 > logs/$(date +%Y%m%d_%H%M%S)/kafka-topics.txt
```

---

이 가이드를 통해 Redis와 Kafka 서비스를 효과적으로 모니터링하고 문제를 해결할 수 있습니다. 정기적인 모니터링을 통해 시스템의 안정성을 유지하세요.