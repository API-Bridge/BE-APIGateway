# API Gateway Helm Chart

이 차트는 Spring Cloud Gateway 기반의 마이크로서비스 API Gateway를 Kubernetes에 배포하기 위한 Helm Chart입니다.

## 기능

- **JWT 인증**: Auth0 JWT 토큰 기반 인증
- **라우팅**: 다중 마이크로서비스 라우팅 지원
- **Rate Limiting**: Redis 기반 분산 Rate Limiting
- **Circuit Breaker**: Resilience4j를 통한 회로 차단기
- **모니터링**: Actuator 및 Prometheus 메트릭 지원
- **로깅**: Kafka 기반 분산 로깅

## 사전 요구사항

- Kubernetes 1.16+
- Helm 3.0+
- Redis (Rate Limiting용)
- Kafka (로깅용, 선택사항)

## 설치

1. 차트 저장소 추가:
```bash
helm repo add your-repo https://your-helm-repo.com
helm repo update
```

2. 기본 설정으로 설치:
```bash
helm install api-gateway ./chart
```

3. 사용자 정의 값으로 설치:
```bash
helm install api-gateway ./chart -f custom-values.yaml
```

## 설정

### 기본 설정

| 파라미터 | 설명 | 기본값 |
|----------|------|--------|
| `replicaCount` | Pod 복제본 수 | `2` |
| `image.repository` | 이미지 저장소 | `api-gateway` |
| `image.tag` | 이미지 태그 | `""` |
| `service.type` | 서비스 타입 | `ClusterIP` |
| `service.port` | 서비스 포트 | `8080` |

### 인증 설정

```yaml
secrets:
  auth0:
    clientId: "your-auth0-client-id"
    clientSecret: "your-auth0-client-secret" 
    issuerUri: "https://your-domain.auth0.com/"
    audience: "your-api-audience"
```

### Redis 설정

```yaml
redis:
  enabled: true
  host: redis-service
  port: 6379
```

### Kafka 설정 (선택사항)

```yaml
kafka:
  enabled: true
  bootstrapServers: kafka-service:9092
```

### 서비스 라우팅 설정

```yaml
gateway:
  routes:
    userService:
      uri: http://user-service:8081
      path: /gateway/users/**
    apiManagement:
      uri: http://api-management-service:8082
      path: /gateway/apimgmt/**
```

### 오토스케일링

```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

## 모니터링

### Health Checks

차트는 자동으로 liveness와 readiness probe를 설정합니다:

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`

### Prometheus Metrics

ServiceMonitor를 활성화하여 Prometheus 메트릭을 수집할 수 있습니다:

```yaml
monitoring:
  enabled: true
  serviceMonitor:
    enabled: true
    namespace: monitoring
```

## 보안

- 비루트 사용자로 실행 (UID: 1001)
- ReadOnlyRootFilesystem 권장
- 최소한의 리소스 권한

## 업그레이드

```bash
helm upgrade api-gateway ./chart -f values.yaml
```

## 제거

```bash
helm uninstall api-gateway
```

## 트러블슈팅

### 일반적인 문제

1. **Pod가 시작되지 않는 경우**
   ```bash
   kubectl describe pod -l app.kubernetes.io/name=api-gateway
   kubectl logs -l app.kubernetes.io/name=api-gateway
   ```

2. **Redis 연결 실패**
   - Redis 서비스가 올바른 네임스페이스에서 실행되고 있는지 확인
   - 네트워크 정책이 트래픽을 차단하지 않는지 확인

3. **Auth0 인증 오류**
   - Secret에 올바른 Auth0 자격 증명이 설정되어 있는지 확인
   - issuer-uri가 올바르게 설정되어 있는지 확인

### 디버깅

```bash
# Pod 상태 확인
kubectl get pods -l app.kubernetes.io/name=api-gateway

# 서비스 엔드포인트 확인
kubectl get endpoints api-gateway

# ConfigMap 확인
kubectl describe configmap api-gateway-config

# 로그 확인
kubectl logs -f deployment/api-gateway
```