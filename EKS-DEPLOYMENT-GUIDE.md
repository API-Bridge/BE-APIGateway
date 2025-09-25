# AWS EKS 배포 가이드

이 가이드는 API Gateway 서비스를 AWS EKS 클러스터에 배포하는 방법을 설명합니다.

## 사전 요구 사항

### 1. AWS 서비스 설정
- **EKS 클러스터**: 1.24 이상 버전
- **Amazon ECR**: 컨테이너 이미지 저장소
- **ElastiCache for Redis**: Rate limiting 및 캐싱
- **Amazon MSK**: Kafka 메시징 (선택사항)
- **AWS Load Balancer Controller**: Ingress 관리

### 2. 로컬 도구 설치
```bash
# AWS CLI
aws --version

# kubectl
kubectl version --client

# Helm 3
helm version

# Docker
docker --version
```

## 1. 컨테이너 이미지 빌드 및 푸시

### ECR 리포지토리 생성
```bash
# ECR 리포지토리 생성
aws ecr create-repository --repository-name api-gateway --region your-region

# Docker 로그인
aws ecr get-login-password --region your-region | docker login --username AWS --password-stdin your-account-id.dkr.ecr.your-region.amazonaws.com
```

### 이미지 빌드 및 푸시
```bash
# Docker 이미지 빌드
docker build -t api-gateway .

# ECR용 태그 생성
docker tag api-gateway:latest your-account-id.dkr.ecr.your-region.amazonaws.com/api-gateway:latest

# ECR에 푸시
docker push your-account-id.dkr.ecr.your-region.amazonaws.com/api-gateway:latest
```

## 2. AWS 인프라 설정

### ElastiCache for Redis
```bash
# Redis 클러스터 생성 (AWS CLI)
aws elasticache create-replication-group \
  --replication-group-id api-gateway-redis \
  --description "Redis for API Gateway" \
  --primary-cluster-id api-gateway-redis-001 \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --engine-version 7.0 \
  --port 6379 \
  --security-group-ids sg-your-security-group \
  --subnet-group-name your-cache-subnet-group
```

### Amazon MSK (선택사항)
```bash
# MSK 클러스터 생성
aws kafka create-cluster \
  --cluster-name api-gateway-kafka \
  --broker-node-group-info file://broker-info.json \
  --kafka-version 2.8.1 \
  --number-of-broker-nodes 3
```

## 3. Kubernetes 클러스터 설정

### EKS 클러스터 연결
```bash
# kubectl 컨텍스트 설정
aws eks update-kubeconfig --region your-region --name your-cluster-name

# 연결 확인
kubectl cluster-info
```

### AWS Load Balancer Controller 설치
```bash
# Helm으로 AWS Load Balancer Controller 설치
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  --set clusterName=your-cluster-name \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  -n kube-system
```

## 4. Helm Chart 배포

### values.yaml 설정 수정
```bash
# chart/values.yaml 파일에서 다음 값들을 실제 값으로 변경:

# 1. ECR 이미지 URI
image:
  repository: your-account-id.dkr.ecr.your-region.amazonaws.com/api-gateway

# 2. Ingress 도메인
ingress:
  hosts:
    - host: api-gateway.your-domain.com

# 3. Redis 엔드포인트
redis:
  host: your-elasticache-redis.cache.amazonaws.com

# 4. Kafka 엔드포인트 (선택사항)
kafka:
  bootstrapServers: your-msk-cluster.kafka.region.amazonaws.com:9092
```

### Secret 생성 (Auth0 인증 정보)
```bash
# Kubernetes Secret 생성
kubectl create secret generic api-gateway-secrets \
  --from-literal=auth0-client-id='your-auth0-client-id' \
  --from-literal=auth0-client-secret='your-auth0-client-secret' \
  --from-literal=auth0-issuer-uri='https://api-bridge.us.auth0.com/' \
  --from-literal=auth0-audience='http://api-gateway:8080/'
```

### Helm 배포
```bash
# Namespace 생성
kubectl create namespace api-gateway

# Helm 배포
helm install api-gateway ./chart \
  --namespace api-gateway \
  --values ./chart/values.yaml

# 배포 상태 확인
kubectl get pods -n api-gateway
kubectl get svc -n api-gateway
kubectl get ingress -n api-gateway
```

## 5. 배포 후 검증

### Pod 상태 확인
```bash
# Pod 로그 확인
kubectl logs -l app.kubernetes.io/name=api-gateway -n api-gateway -f

# Pod 상세 정보
kubectl describe pods -l app.kubernetes.io/name=api-gateway -n api-gateway
```

### Health Check
```bash
# Port Forward로 로컬 테스트
kubectl port-forward svc/api-gateway 8080:8080 -n api-gateway

# Health Check
curl http://localhost:8080/actuator/health
```

### Ingress 확인
```bash
# ALB 생성 확인
kubectl get ingress api-gateway -n api-gateway

# ALB DNS 이름 확인
kubectl get ingress api-gateway -n api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

## 6. 모니터링 및 로깅

### CloudWatch 로그 그룹 설정
```bash
# Fluent Bit를 통한 로그 수집 (선택사항)
helm repo add fluent https://fluent.github.io/helm-charts
helm install fluent-bit fluent/fluent-bit \
  --set cloudWatchLogs.enabled=true \
  --set cloudWatchLogs.region=your-region \
  --set cloudWatchLogs.logGroupName=/aws/eks/api-gateway
```

### Prometheus 메트릭 (선택사항)
```bash
# Prometheus Operator 설치
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack
```

## 7. Auto Scaling 설정

### Horizontal Pod Autoscaler (HPA)
```bash
# HPA 확인
kubectl get hpa -n api-gateway

# HPA 상세 정보
kubectl describe hpa api-gateway -n api-gateway
```

### Cluster Autoscaler 설정
```bash
# Cluster Autoscaler 설치 (필요시)
helm repo add autoscaler https://kubernetes.github.io/autoscaler
helm install cluster-autoscaler autoscaler/cluster-autoscaler \
  --set autoDiscovery.clusterName=your-cluster-name \
  --set awsRegion=your-region
```

## 8. 업데이트 및 롤백

### 애플리케이션 업데이트
```bash
# 새 이미지 빌드 및 푸시 후
helm upgrade api-gateway ./chart \
  --namespace api-gateway \
  --values ./chart/values.yaml

# 롤링 업데이트 상태 확인
kubectl rollout status deployment api-gateway -n api-gateway
```

### 롤백
```bash
# 배포 히스토리 확인
helm history api-gateway -n api-gateway

# 이전 버전으로 롤백
helm rollback api-gateway 1 -n api-gateway
```

## 9. 트러블슈팅

### 일반적인 문제 해결

#### Pod 시작 실패
```bash
# Pod 이벤트 확인
kubectl describe pods -l app.kubernetes.io/name=api-gateway -n api-gateway

# 리소스 부족 확인
kubectl top nodes
kubectl top pods -n api-gateway
```

#### Ingress 문제
```bash
# ALB Controller 로그 확인
kubectl logs -n kube-system deployment/aws-load-balancer-controller

# Security Group 규칙 확인
aws ec2 describe-security-groups --group-ids sg-your-alb-sg
```

#### Redis 연결 문제
```bash
# Redis 연결 테스트
kubectl run redis-test --image=redis:alpine -it --rm --restart=Never -- redis-cli -h your-redis-host ping
```

### 유용한 명령어
```bash
# 모든 리소스 확인
kubectl get all -n api-gateway

# 로그 스트리밍
kubectl logs -f deployment/api-gateway -n api-gateway

# Shell 접속
kubectl exec -it deployment/api-gateway -n api-gateway -- /bin/sh

# 설정 확인
kubectl get configmap api-gateway-config -n api-gateway -o yaml
```

## 10. 보안 고려사항

### Network Policy (선택사항)
```yaml
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-network-policy
  namespace: api-gateway
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: api-gateway
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from: []
  egress:
  - to: []
```

### RBAC 설정
```yaml
# rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: api-gateway-sa
  namespace: api-gateway
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: api-gateway-role
  namespace: api-gateway
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
```

## 참고 자료

- [AWS EKS 사용 설명서](https://docs.aws.amazon.com/eks/)
- [Helm 공식 문서](https://helm.sh/docs/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)
- [Spring Cloud Gateway 문서](https://spring.io/projects/spring-cloud-gateway)