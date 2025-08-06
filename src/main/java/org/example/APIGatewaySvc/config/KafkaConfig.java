package org.example.APIGatewaySvc.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Apache Kafka 메시징 시스템 설정 클래스
 * MSA 환경에서 마이크로서비스 간 비동기 메시지 통신을 위한 Kafka 설정
 * 
 * 주요 기능:
 * - Kafka Producer 설정 (메시지 전송용)
 * - Kafka Consumer 설정 (메시지 수신용)
 * - JSON 직렬화/역직렬화 설정
 * - Kafka Listener Container Factory 설정
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /** Kafka 브로커 서버 주소 */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** Kafka 컴슈머 그룹 ID */
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Kafka Producer Factory 빈 설정
     * 메시지를 Kafka 토픽에 전송하기 위한 Producer 구성
     * 
     * @return ProducerFactory<String, Object> Kafka Producer Factory
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Kafka Template 빈 설정
     * Kafka Producer를 감싸서 간편하게 메시지를 전송할 수 있도록 지원
     * 
     * @return KafkaTemplate<String, Object> Kafka 메시지 전송 템플릿
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Consumer Factory 빈 설정
     * Kafka 토픽의 메시지를 수신하기 위한 Consumer 구성
     * 
     * @return ConsumerFactory<String, Object> Kafka Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka Listener Container Factory 빈 설정
     * @KafkaListener 애노테이션을 사용하여 메시지를 비동기로 처리할 수 있도록 지원
     * 
     * @return ConcurrentKafkaListenerContainerFactory<String, Object> Kafka Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}