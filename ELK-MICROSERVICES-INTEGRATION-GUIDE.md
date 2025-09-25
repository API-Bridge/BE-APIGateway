# 🚀 ELK 스택 마이크로서비스 통합 가이드

다른 마이크로서비스에서도 동일한 ELK 로그 수집을 적용하기 위한 완전한 가이드입니다.

## 📋 **1. 전제 조건**

- Docker & Docker Compose 환경
- Spring Boot 애플리케이션 (다른 프레임워크도 유사하게 적용 가능)
- 중앙 ELK 스택이 이미 구축되어 있음

## 🎯 **2. 마이크로서비스별 적용 단계**

### **Step 1: Logback 설정 (Spring Boot)**

각 마이크로서비스의 `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 콘솔 출력용 (로컬 개발) -->
    <springProfile name="dev,local">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>

    <!-- Docker 환경용 JSON 로그 -->
    <springProfile name="docker,prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
                <providers>
                    <timestamp>
                        <pattern>yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX</pattern>
                    </timestamp>
                    <version/>
                    <logLevel/>
                    <message/>
                    <mdc/>
                    <loggerName/>
                    <thread/>
                    <stackTrace/>
                    <pattern>
                        <pattern>
                        {
                            "springAppName": "${SPRING_APPLICATION_NAME:-unknown}",
                            "service": "${SERVICE_NAME:-unknown}",
                            "pid": "${PID:-unknown}"
                        }
                        </pattern>
                    </pattern>
                </providers>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>
</configuration>
```

### **Step 2: 의존성 추가**

`build.gradle` 또는 `pom.xml`에 JSON 로깅 라이브러리 추가:

**Gradle:**
```gradle
dependencies {
    implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
}
```

**Maven:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### **Step 3: Application 설정**

각 마이크로서비스의 `application.yml`:

```yaml
spring:
  application:
    name: your-service-name  # 서비스명 수정 필요
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:prod}

# Docker 환경 설정
---
spring:
  config:
    activate:
      on-profile: docker
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}

# Elasticsearch 설정 (옵셔널)
elasticsearch:
  host: ${ELASTICSEARCH_HOST:elasticsearch}
  port: ${ELASTICSEARCH_PORT:9200}

logging:
  level:
    your.package.name: DEBUG  # 패키지명 수정 필요
    org.springframework.security: DEBUG
```

### **Step 4: Docker Compose 설정**

각 마이크로서비스의 `docker-compose.yml` 또는 통합 파일:

```yaml
version: '3.8'

services:
  your-service-name:  # 서비스명 수정 필요
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8081:8080"  # 포트 수정 필요
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_APPLICATION_NAME=your-service-name  # 서비스명 수정 필요
      - SERVICE_NAME=your-service-name  # 서비스명 수정 필요
      
      # 공통 인프라 서비스 연결
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_DATA_REDIS_PORT=6379
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - ELASTICSEARCH_HOST=elasticsearch
      - ELASTICSEARCH_PORT=9200
      
      # 데이터베이스 (각 서비스별 수정 필요)
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/your_service_db
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=password
      
    # JSON 로그 형식으로 출력
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
        
    depends_on:
      redis:
        condition: service_healthy
      # 필요한 다른 서비스들...
        
    networks:
      - microservices-network

# 공통 네트워크 (ELK 스택과 동일)
networks:
  microservices-network:
    external: true  # 또는 ELK 스택과 공유하는 네트워크
```

## 🔧 **3. 중앙 ELK 스택 설정 업데이트**

### **Logstash 파이프라인 수정**

`logstash/pipeline/microservices-logs.conf`:

```ruby
input {
  beats {
    port => 5044
  }
}

filter {
  # 모든 마이크로서비스의 JSON 로그 파싱
  if [message] =~ /^\{.*\}$/ {
    json {
      source => "message"
      target => "json"
    }
    
    if [json] {
      # 로그 레벨별 태깅
      if [json][level] == "ERROR" {
        mutate { add_tag => [ "error" ] }
      }
      if [json][level] == "WARN" {
        mutate { add_tag => [ "warning" ] }
      }
      if [json][level] == "INFO" {
        mutate { add_tag => [ "info" ] }
      }
      
      # 공통 필드 생성
      mutate {
        add_field => {
          "service_name" => "%{[json][service]}"
          "app_name" => "%{[json][springAppName]}"
          "log_level" => "%{[json][level]}"
          "logger_name" => "%{[json][logger]}"
          "thread_name" => "%{[json][thread]}"
        }
      }
      
      # 서비스별 인덱스 생성을 위한 필드
      mutate {
        add_field => { "index_service" => "%{[json][service]}" }
      }
      
      # 불필요한 필드 정리
      mutate {
        remove_field => [ "agent", "ecs", "host", "input" ]
        remove_tag => [ "_jsonparsefailure" ]
      }
    }
  }
  
  # 특정 서비스만 필터링하고 싶다면 (옵셔널)
  # if [json][service] not in ["service1", "service2", "service3"] {
  #   drop { }
  # }
}

output {
  # 서비스별 인덱스 생성
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "%{[json][service]:-unknown}-logs-%{+YYYY.MM.dd}"
  }
  
  # 통합 인덱스도 생성하고 싶다면
  # elasticsearch {
  #   hosts => ["elasticsearch:9200"]
  #   index => "microservices-logs-%{+YYYY.MM.dd}"
  # }
  
  # 개발용 디버그 (필요시)
  # stdout { codec => rubydebug }
}
```

### **Filebeat 설정**

`filebeat/filebeat.yml` (기존 설정에 추가):

```yaml
filebeat.inputs:
- type: container
  paths:
    - '/var/lib/docker/containers/*/*.log'
  processors:
  - add_docker_metadata:
      host: "unix:///var/run/docker.sock"
  - decode_json_fields:
      fields: ["message"]
      target: ""
      overwrite_keys: true

# 특정 서비스만 수집하고 싶다면
# include_lines: ['.*"service":"(service1|service2|service3)".*']

output.logstash:
  hosts: ["logstash:5044"]

logging.level: info
```

## 📊 **4. Kibana 인덱스 패턴 설정**

### **서비스별 인덱스 패턴:**
- `user-service-logs-*`
- `order-service-logs-*`  
- `payment-service-logs-*`

### **통합 인덱스 패턴:**
- `*-logs-*` (모든 서비스)

## 🎯 **5. 새 마이크로서비스 추가 체크리스트**

```markdown
### 🔄 새 서비스 추가 체크리스트

#### **애플리케이션 설정**
- [ ] `logback-spring.xml` 파일 생성
- [ ] `build.gradle`/`pom.xml`에 logstash-logback-encoder 의존성 추가
- [ ] `application.yml`에 Docker 프로파일 설정 추가
- [ ] 서비스명 환경변수 설정 (`SPRING_APPLICATION_NAME`, `SERVICE_NAME`)

#### **Docker 설정**  
- [ ] `docker-compose.yml` 환경변수 설정
- [ ] JSON 로깅 드라이버 설정
- [ ] 네트워크 연결 (ELK 스택과 동일 네트워크)
- [ ] 포트 충돌 확인

#### **ELK 스택 설정**
- [ ] Logstash 파이프라인에서 새 서비스 로그 수집 확인
- [ ] Kibana에서 새 인덱스 패턴 생성
- [ ] 로그 수집 테스트

#### **검증**
- [ ] Docker 컨테이너 정상 시작
- [ ] Elasticsearch에 로그 인덱스 생성 확인
- [ ] Kibana에서 로그 검색 테스트
- [ ] 로그 레벨별 필터링 테스트
```

## 🚨 **6. 문제해결 가이드**

### **일반적인 문제들:**

1. **JSON 파싱 실패**
   ```bash
   # Logstash에서 _jsonparsefailure 태그 확인
   GET *-logs-*/_search
   {
     "query": {"match": {"tags": "_jsonparsefailure"}},
     "size": 5
   }
   ```

2. **로그가 수집되지 않음**
   ```bash
   # 컨테이너 로그 확인
   docker-compose logs your-service-name
   
   # Filebeat 상태 확인
   docker-compose logs filebeat
   ```

3. **인덱스가 생성되지 않음**
   ```bash
   # Elasticsearch 인덱스 확인
   curl -X GET "localhost:9200/_cat/indices?v"
   ```

## 🎯 **7. 표준 서비스별 설정 템플릿**

### **User Service 예시:**
```yaml
# docker-compose.yml 중 user-service 설정
user-service:
  environment:
    - SPRING_APPLICATION_NAME=user-service
    - SERVICE_NAME=user-service
    - SERVER_PORT=8081
    # ... 기타 설정
```

### **Order Service 예시:**
```yaml
# docker-compose.yml 중 order-service 설정  
order-service:
  environment:
    - SPRING_APPLICATION_NAME=order-service
    - SERVICE_NAME=order-service
    - SERVER_PORT=8082
    # ... 기타 설정
```

## 📋 **8. 주요 명령어 모음**

### **ELK 스택 관리**
```bash
# 전체 스택 시작
docker-compose up -d

# 특정 서비스 재시작
docker-compose restart system-management-service

# 로그 확인
docker-compose logs -f system-management-service

# 상태 확인
docker-compose ps
```

### **Elasticsearch 쿼리**
```bash
# 인덱스 확인
curl -X GET "localhost:9200/_cat/indices?v"

# 최신 로그 확인
curl -X GET "localhost:9200/system-management-logs-*/_search?size=5&sort=@timestamp:desc&pretty"

# 에러 로그만 검색
curl -X GET "localhost:9200/*-logs-*/_search?q=json.level:ERROR&pretty"
```

### **Kibana Dev Tools 쿼리**
```json
# 최신 로그 5개
GET *-logs-*/_search
{
  "size": 5,
  "sort": [{"@timestamp": {"order": "desc"}}]
}

# ERROR 로그만 검색
GET *-logs-*/_search
{
  "query": {"match": {"json.level": "ERROR"}},
  "size": 10,
  "sort": [{"@timestamp": {"order": "desc"}}]
}
```

### **Kibana Discover 검색어**
```bash
# 로그 레벨별 검색
json.level: "ERROR"
json.level: "WARN"

# 서비스별 검색
json.service: "user-service"
json.service: "order-service"

# 복합 검색
json.level: "ERROR" AND json.service: "user-service"
```

## 🔍 **9. 모니터링 및 알림 설정**

### **ElastAlert 설정 예시** (옵셔널)
```yaml
# elastalert/rules/error-alert.yml
name: Microservices Error Alert
type: frequency
index: *-logs-*
num_events: 5
timeframe:
  minutes: 5

filter:
- term:
    json.level.keyword: "ERROR"

alert:
- "email"
- "slack"

email:
- "devops@company.com"

slack:
webhook_url: "https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK"
slack_channel_override: "#alerts"
```

## 🚀 **10. 성능 최적화 팁**

1. **인덱스 라이프사이클 관리**
   - 7일 후 삭제
   - Hot → Warm → Cold → Delete 정책

2. **샤드 설정**
   - 작은 서비스: 1개 샤드
   - 대용량 서비스: 3-5개 샤드

3. **필드 매핑 최적화**
   - keyword vs text 필드 구분
   - 불필요한 필드 제거

## ☁️ **11. EKS 환경 배포 시**

EKS(Amazon Elastic Kubernetes Service) 환경에서 ELK 스택을 배포할 때는 Docker Compose 대신 Kubernetes 리소스로 변환해야 합니다.

### **11.1 아키텍처 변경사항**

```
Docker Compose 환경          →          EKS 환경
├── docker-compose.yml       →          ├── k8s/elasticsearch/
├── filebeat/                →          ├── k8s/kibana/
├── logstash/                →          ├── k8s/logstash/
└── monitoring/              →          ├── k8s/filebeat/
                                       └── k8s/microservices/
```

### **11.2 Elasticsearch Kubernetes 배포**

**elasticsearch-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: elasticsearch
  namespace: elk-system
spec:
  serviceName: elasticsearch
  replicas: 1
  selector:
    matchLabels:
      app: elasticsearch
  template:
    metadata:
      labels:
        app: elasticsearch
    spec:
      containers:
      - name: elasticsearch
        image: docker.elastic.co/elasticsearch/elasticsearch:7.17.0
        ports:
        - containerPort: 9200
        - containerPort: 9300
        env:
        - name: discovery.type
          value: single-node
        - name: ES_JAVA_OPTS
          value: "-Xms512m -Xmx512m"
        - name: network.host
          value: "0.0.0.0"
        volumeMounts:
        - name: elasticsearch-data
          mountPath: /usr/share/elasticsearch/data
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
  volumeClaimTemplates:
  - metadata:
      name: elasticsearch-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: gp3  # AWS EBS gp3
      resources:
        requests:
          storage: 20Gi

---
apiVersion: v1
kind: Service
metadata:
  name: elasticsearch
  namespace: elk-system
spec:
  selector:
    app: elasticsearch
  ports:
  - port: 9200
    targetPort: 9200
    name: http
  - port: 9300
    targetPort: 9300
    name: transport
```

### **11.3 Logstash Kubernetes 배포**

**logstash-configmap.yaml:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: logstash-config
  namespace: elk-system
data:
  logstash.conf: |
    input {
      beats {
        port => 5044
      }
    }
    
    filter {
      if [message] =~ /^\{.*\}$/ {
        json {
          source => "message"
          target => "json"
        }
        
        if [json] {
          if [json][level] == "ERROR" {
            mutate { add_tag => [ "error" ] }
          }
          if [json][level] == "WARN" {
            mutate { add_tag => [ "warning" ] }
          }
          if [json][level] == "INFO" {
            mutate { add_tag => [ "info" ] }
          }
          
          mutate {
            add_field => {
              "service_name" => "%{[json][service]}"
              "app_name" => "%{[json][springAppName]}"
              "log_level" => "%{[json][level]}"
              "logger_name" => "%{[json][logger]}"
              "thread_name" => "%{[json][thread]}"
              "kubernetes_namespace" => "%{[kubernetes][namespace]}"
              "kubernetes_pod" => "%{[kubernetes][pod][name]}"
            }
          }
          
          mutate {
            remove_field => [ "agent", "ecs", "host", "input" ]
            remove_tag => [ "_jsonparsefailure" ]
          }
        }
      }
    }
    
    output {
      elasticsearch {
        hosts => ["http://elasticsearch:9200"]
        index => "%{[json][service]:-unknown}-logs-%{+YYYY.MM.dd}"
      }
    }

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: logstash
  namespace: elk-system
spec:
  replicas: 2
  selector:
    matchLabels:
      app: logstash
  template:
    metadata:
      labels:
        app: logstash
    spec:
      containers:
      - name: logstash
        image: docker.elastic.co/logstash/logstash:7.17.0
        ports:
        - containerPort: 5044
        - containerPort: 9600
        volumeMounts:
        - name: logstash-config
          mountPath: /usr/share/logstash/pipeline
        env:
        - name: LS_JAVA_OPTS
          value: "-Xms512m -Xmx512m"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
      volumes:
      - name: logstash-config
        configMap:
          name: logstash-config

---
apiVersion: v1
kind: Service
metadata:
  name: logstash
  namespace: elk-system
spec:
  selector:
    app: logstash
  ports:
  - port: 5044
    targetPort: 5044
    name: beats
  - port: 9600
    targetPort: 9600
    name: http
```

### **11.4 Filebeat DaemonSet 배포**

**filebeat-daemonset.yaml:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
  namespace: elk-system
data:
  filebeat.yml: |
    filebeat.inputs:
    - type: container
      paths:
        - /var/log/containers/*.log
      processors:
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
          - logs_path:
              logs_path: "/var/log/containers/"
      - decode_json_fields:
          fields: ["message"]
          target: ""
          overwrite_keys: true
    
    output.logstash:
      hosts: ["logstash:5044"]
    
    logging.level: info

---
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: filebeat
  namespace: elk-system
spec:
  selector:
    matchLabels:
      app: filebeat
  template:
    metadata:
      labels:
        app: filebeat
    spec:
      serviceAccountName: filebeat
      terminationGracePeriodSeconds: 30
      hostNetwork: true
      dnsPolicy: ClusterFirstWithHostNet
      containers:
      - name: filebeat
        image: docker.elastic.co/beats/filebeat:7.17.0
        args: [
          "-c", "/etc/filebeat.yml",
          "-e",
        ]
        env:
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        securityContext:
          runAsUser: 0
        resources:
          limits:
            memory: 200Mi
            cpu: 100m
          requests:
            memory: 100Mi
            cpu: 100m
        volumeMounts:
        - name: config
          mountPath: /etc/filebeat.yml
          readOnly: true
          subPath: filebeat.yml
        - name: data
          mountPath: /usr/share/filebeat/data
        - name: varlibdockercontainers
          mountPath: /var/log/containers
          readOnly: true
        - name: varlog
          mountPath: /var/log
          readOnly: true
      volumes:
      - name: config
        configMap:
          defaultMode: 0640
          name: filebeat-config
      - name: varlibdockercontainers
        hostPath:
          path: /var/log/containers
      - name: varlog
        hostPath:
          path: /var/log
      - name: data
        hostPath:
          path: /var/lib/filebeat-data
          type: DirectoryOrCreate

---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: filebeat
  namespace: elk-system

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: filebeat
rules:
- apiGroups: [""]
  resources:
  - nodes
  - namespaces
  - events
  - pods
  verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: filebeat
subjects:
- kind: ServiceAccount
  name: filebeat
  namespace: elk-system
roleRef:
  kind: ClusterRole
  name: filebeat
  apiGroup: rbac.authorization.k8s.io
```

### **11.5 Kibana 배포**

**kibana-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kibana
  namespace: elk-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: kibana
  template:
    metadata:
      labels:
        app: kibana
    spec:
      containers:
      - name: kibana
        image: docker.elastic.co/kibana/kibana:7.17.0
        ports:
        - containerPort: 5601
        env:
        - name: ELASTICSEARCH_HOSTS
          value: "http://elasticsearch:9200"
        - name: SERVER_PUBLICBASEURL
          value: "https://your-kibana-domain.com"  # ALB 도메인
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"

---
apiVersion: v1
kind: Service
metadata:
  name: kibana
  namespace: elk-system
spec:
  selector:
    app: kibana
  ports:
  - port: 5601
    targetPort: 5601

---
# ALB Ingress for Kibana
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: kibana-ingress
  namespace: elk-system
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:region:account:certificate/cert-id  # ACM 인증서
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
    alb.ingress.kubernetes.io/ssl-redirect: '443'
spec:
  rules:
  - host: your-kibana-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: kibana
            port:
              number: 5601
```

### **11.6 마이크로서비스 배포**

**microservice-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: microservices
spec:
  replicas: 3
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
      - name: user-service
        image: your-registry/user-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: SPRING_APPLICATION_NAME
          value: "user-service"
        - name: SERVICE_NAME
          value: "user-service"
        - name: SPRING_DATA_REDIS_HOST
          value: "redis"
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        # 로그 수집을 위한 JSON 로깅 (stdout)
        # logback-spring.xml의 k8s 프로파일 사용

---
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: microservices
spec:
  selector:
    app: user-service
  ports:
  - port: 8080
    targetPort: 8080
```

### **11.7 Application 설정 변경**

**application.yml에 k8s 프로파일 추가:**
```yaml
---
# Kubernetes 환경 설정
spring:
  config:
    activate:
      on-profile: k8s
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}

elasticsearch:
  host: ${ELASTICSEARCH_HOST:elasticsearch.elk-system.svc.cluster.local}
  port: ${ELASTICSEARCH_PORT:9200}

logging:
  level:
    your.package.name: INFO
    org.springframework.security: WARN
```

**logback-spring.xml에 k8s 프로파일 추가:**
```xml
<!-- Kubernetes 환경용 JSON 로그 -->
<springProfile name="k8s">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp>
                    <pattern>yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX</pattern>
                </timestamp>
                <version/>
                <logLevel/>
                <message/>
                <mdc/>
                <loggerName/>
                <thread/>
                <stackTrace/>
                <pattern>
                    <pattern>
                    {
                        "springAppName": "${SPRING_APPLICATION_NAME:-unknown}",
                        "service": "${SERVICE_NAME:-unknown}",
                        "namespace": "${KUBERNETES_NAMESPACE:-unknown}",
                        "pod": "${HOSTNAME:-unknown}"
                    }
                    </pattern>
                </pattern>
            </providers>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</springProfile>
```

### **11.8 배포 명령어**

```bash
# 네임스페이스 생성
kubectl create namespace elk-system
kubectl create namespace microservices

# ELK 스택 배포
kubectl apply -f k8s/elasticsearch/
kubectl apply -f k8s/logstash/
kubectl apply -f k8s/filebeat/
kubectl apply -f k8s/kibana/

# 마이크로서비스 배포
kubectl apply -f k8s/microservices/

# 상태 확인
kubectl get pods -n elk-system
kubectl get pods -n microservices

# Kibana 접속 (포트 포워딩)
kubectl port-forward -n elk-system svc/kibana 5601:5601
```

### **11.9 모니터링 및 관리**

```bash
# 로그 확인
kubectl logs -n microservices deployment/user-service -f

# Elasticsearch 인덱스 확인
kubectl exec -n elk-system deployment/elasticsearch -- curl -X GET "localhost:9200/_cat/indices?v"

# 리소스 사용량 확인
kubectl top pods -n elk-system
kubectl top pods -n microservices
```

### **11.10 주요 차이점 요약**

| 구분 | Docker Compose | EKS (Kubernetes) |
|------|----------------|------------------|
| **배포 방식** | `docker-compose up` | `kubectl apply -f` |
| **네트워킹** | Docker 네트워크 | Kubernetes Service |
| **스토리지** | Docker Volume | PVC + EBS |
| **로그 수집** | Container 로그 | Pod stdout |
| **설정 관리** | 환경변수 | ConfigMap + Secret |
| **스케일링** | `scale` 명령어 | HPA / VPA |
| **로드밸런싱** | 내장 LB | AWS ALB |
| **보안** | Docker 격리 | RBAC + Pod Security |

이 가이드를 따라하면 모든 마이크로서비스에서 **동일한 품질의 구조화된 로그**를 ELK 스택으로 수집할 수 있습니다! 🚀