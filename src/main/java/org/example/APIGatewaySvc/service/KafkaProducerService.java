package org.example.APIGatewaySvc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer 서비스
 * 게이트웨이 로그 및 이벤트를 Kafka 토픽으로 전송
 */
@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 게이트웨이 로그 이벤트를 Kafka 토픽으로 전송
     */
    public void sendGatewayLog(Object logEvent) {
        sendMessage("logs.gateway", logEvent);
    }

    /**
     * 사용자 인증 이벤트를 Kafka 토픽으로 전송
     */
    public void sendAuthEvent(Object authEvent) {
        sendMessage("events.auth", authEvent);
    }

    /**
     * Rate Limit 이벤트를 Kafka 토픽으로 전송
     */
    public void sendRateLimitEvent(Object rateLimitEvent) {
        sendMessage("events.ratelimit", rateLimitEvent);
    }

    /**
     * 일반적인 메시지 전송 메소드
     */
    public void sendMessage(String topic, Object message) {
        sendMessage(topic, null, message);
    }

    /**
     * 키가 있는 메시지 전송 메소드
     */
    public void sendMessage(String topic, String key, Object message) {
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, message);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Kafka 메시지 전송 성공 - Topic: {}, Key: {}, Offset: {}", 
                             topic, key, result.getRecordMetadata().offset());
                } else {
                    log.error("Kafka 메시지 전송 실패 - Topic: {}, Key: {}, Error: {}", 
                             topic, key, ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Kafka 메시지 전송 중 예외 발생 - Topic: {}, Key: {}, Error: {}", 
                     topic, key, e.getMessage(), e);
        }
    }

    /**
     * 동기식 메시지 전송 (테스트용)
     */
    public boolean sendMessageSync(String topic, String key, Object message) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, message).get();
            log.info("Kafka 동기 전송 성공 - Topic: {}, Key: {}, Offset: {}", 
                    topic, key, result.getRecordMetadata().offset());
            return true;
        } catch (Exception e) {
            log.error("Kafka 동기 전송 실패 - Topic: {}, Key: {}, Error: {}", 
                     topic, key, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Kafka 연결 상태 확인
     */
    public boolean isKafkaAvailable() {
        try {
            // 간단한 메타데이터 요청으로 연결 상태 확인
            kafkaTemplate.getProducerFactory().createProducer().partitionsFor("test-topic");
            return true;
        } catch (Exception e) {
            log.warn("Kafka 연결 확인 실패: {}", e.getMessage());
            return false;
        }
    }
}