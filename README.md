# API Integration Service

MSA(Microservice Architecture) 환경에서 동작하는 API 브리지 마이크로서비스입니다.

## 🔧 기술 스택

- **Framework**: Spring Boot 3.5.4
- **Java**: Amazon Corretto 17
- **Authentication**: Auth0 JWT
- **Database**: MySQL (외부 관리)
- **Cache**: Redis (외부 관리)
- **Messaging**: Apache Kafka (외부 관리)
- **Monitoring**: Prometheus + Actuator

## 📦 마이크로서비스 특징

- **Stateless**: 세션을 사용하지 않는 JWT 기반 인증
- **Event-Driven**: Kafka를 통한 비동기 이벤트 통신
- **Resilience**: Circuit Breaker, Retry 패턴 적용
- **Observability**: Metrics, Health Check, Distributed Tracing 지원
- **Container-First**: Docker 컨테이너 최적화

## 🏗️ 외부 의존성

이 마이크로서비스는 다음 공유 인프라에 의존합니다:

- **MySQL**: 데이터 저장소
- **Redis**: 캐싱 및 세션 스토어
- **Kafka**: 이벤트 메시징
- **Auth0**: 인증 서비스

## 🚀 로컬 개발 환경 구성

### 1. 외부 의존성 실행
```bash
# 로컬 개발용 인프라 시작
docker-compose -f docker-compose.local.yml up -d
```

### 2. 애플리케이션 실행
```bash
# Gradle로 실행
./gradlew bootRun

# 또는 IDE에서 CustomApiSvcApplication 실행
```

### 3. 동작 확인
```bash
# Health Check
curl http://localhost:8080/api/v1/health

# Swagger UI
open http://localhost:8080/api/v1/swagger-ui.html
```

## 🐳 컨테이너 빌드 및 실행

### Docker 이미지 빌드
```bash
docker build -t custom-api-svc:latest .
```

### 컨테이너 실행 (환경변수 필요)
```bash
docker run -p 8080:8080 \
  -e DB_URL="jdbc:mysql://your-mysql:3306/customapi" \
  -e DB_USERNAME="app" \
  -e DB_PASSWORD="your-password" \
  -e KAFKA_BOOTSTRAP_SERVERS="your-kafka:9092" \
  -e REDIS_HOST="your-redis" \
  -e AUTH0_ISSUER_URI="https://your-auth0-domain.auth0.com/" \
  -e AUTH0_AUDIENCE="https://api.your-service.com" \
  custom-api-svc:latest
```

## 🔧 설정

### 필수 환경변수
```bash
# 데이터베이스
DB_URL=jdbc:mysql://mysql-host:3306/customapi
DB_USERNAME=your-username
DB_PASSWORD=your-password

# 캐시
REDIS_HOST=redis-host
REDIS_PORT=6379

# 메시징
KAFKA_BOOTSTRAP_SERVERS=kafka-host:9092

# 인증
AUTH0_ISSUER_URI=https://your-domain.auth0.com/
AUTH0_AUDIENCE=https://api.your-service.com

# 애플리케이션
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
```

## 📊 모니터링 엔드포인트

- **Health Check**: `/api/v1/health`
- **Metrics**: `/actuator/prometheus`
- **Info**: `/actuator/info`

## 🧪 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행
./gradlew test --tests="*ControllerTest"
./gradlew test --tests="*IntegrationTest"
```

## 🚀 배포

### Kubernetes 배포 예시
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: custom-api-svc
spec:
  replicas: 3
  selector:
    matchLabels:
      app: custom-api-svc
  template:
    metadata:
      labels:
        app: custom-api-svc
    spec:
      containers:
      - name: custom-api-svc
        image: custom-api-svc:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        # 기타 환경변수들...
        livenessProbe:
          httpGet:
            path: /api/v1/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/v1/health
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

## 🔧 개발 가이드라인

1. **API First**: OpenAPI 스펙을 먼저 정의하고 구현
2. **Event-Driven**: 서비스 간 통신은 이벤트 기반으로 구현
3. **Stateless**: 상태를 저장하지 않는 무상태 서비스
4. **Testable**: 단위 테스트, 통합 테스트 작성
5. **Observable**: 로깅, 메트릭, 트레이싱 구현
6. **Secure**: JWT 인증 및 권한 검증 구현

## 📝 API 문서

Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`