@echo off
echo Starting API Gateway with shared Redis and Kafka resources...
set SPRING_REDIS_PORT=6381
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
gradlew.bat bootRun