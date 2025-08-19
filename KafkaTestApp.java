import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 간단한 Kafka Producer/Consumer 테스트 애플리케이션
 * API Gateway 없이 Kafka 연동만 테스트
 */
public class KafkaTestApp {
    
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC_GATEWAY_LOGS = "logs.gateway";
    private static final String TOPIC_AUTH_EVENTS = "events.auth";
    private static final String TOPIC_RATELIMIT = "events.ratelimit";
    
    public static void main(String[] args) {
        System.out.println("=== Kafka 연동 테스트 시작 ===");
        
        try {
            // 1. Producer 테스트
            testProducer();
            
            // 잠시 대기
            Thread.sleep(2000);
            
            // 2. Consumer 테스트
            testConsumer();
            
        } catch (Exception e) {
            System.err.println("테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== Kafka 연동 테스트 완료 ===");
    }
    
    /**
     * Kafka Producer 테스트
     */
    private static void testProducer() {
        System.out.println("\n🚀 Producer 테스트 시작");
        
        // Producer 설정
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            
            // 게이트웨이 로그 메시지 전송
            String gatewayLog = createGatewayLogJson();
            producer.send(new ProducerRecord<>(TOPIC_GATEWAY_LOGS, "gateway-log", gatewayLog));
            System.out.println("✅ Gateway Log 전송: " + gatewayLog);
            
            // 인증 이벤트 메시지 전송
            String authEvent = createAuthEventJson();
            producer.send(new ProducerRecord<>(TOPIC_AUTH_EVENTS, "auth-event", authEvent));
            System.out.println("✅ Auth Event 전송: " + authEvent);
            
            // Rate Limit 이벤트 메시지 전송
            String rateLimitEvent = createRateLimitEventJson();
            producer.send(new ProducerRecord<>(TOPIC_RATELIMIT, "ratelimit-event", rateLimitEvent));
            System.out.println("✅ Rate Limit Event 전송: " + rateLimitEvent);
            
            // 전송 완료 대기
            producer.flush();
            System.out.println("📤 모든 메시지 전송 완료!");
            
        } catch (Exception e) {
            System.err.println("❌ Producer 테스트 실패: " + e.getMessage());
        }
    }
    
    /**
     * Kafka Consumer 테스트
     */
    private static void testConsumer() {
        System.out.println("\n📥 Consumer 테스트 시작");
        
        // Consumer 설정
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            
            // 모든 토픽 구독
            consumer.subscribe(Arrays.asList(TOPIC_GATEWAY_LOGS, TOPIC_AUTH_EVENTS, TOPIC_RATELIMIT));
            
            System.out.println("📖 메시지 수신 대기 중... (10초)");
            
            // 10초 동안 메시지 수신
            long endTime = System.currentTimeMillis() + 10000;
            int messageCount = 0;
            
            while (System.currentTimeMillis() < endTime) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    messageCount++;
                    System.out.printf("📨 [%s] Key: %s, Value: %s%n", 
                        record.topic(), record.key(), record.value());
                }
                
                if (messageCount >= 3) { // 3개 메시지 받으면 종료
                    break;
                }
            }
            
            System.out.println("📊 총 " + messageCount + "개 메시지 수신 완료!");
            
        } catch (Exception e) {
            System.err.println("❌ Consumer 테스트 실패: " + e.getMessage());
        }
    }
    
    // JSON 메시지 생성 메소드들
    private static String createGatewayLogJson() {
        return String.format(
            "{\"eventType\":\"REQUEST\",\"requestId\":\"req-%d\",\"method\":\"GET\",\"path\":\"/test/api\",\"statusCode\":200,\"responseTime\":45,\"clientIp\":\"127.0.0.1\",\"timestamp\":\"%s\"}",
            System.currentTimeMillis(), Instant.now().toString()
        );
    }
    
    private static String createAuthEventJson() {
        return String.format(
            "{\"eventType\":\"LOGIN_SUCCESS\",\"userId\":\"user-%d\",\"clientIp\":\"192.168.1.100\",\"provider\":\"auth0\",\"timestamp\":\"%s\"}",
            System.currentTimeMillis(), Instant.now().toString()
        );
    }
    
    private static String createRateLimitEventJson() {
        return String.format(
            "{\"eventType\":\"RATE_LIMIT_EXCEEDED\",\"clientKey\":\"ip:127.0.0.1\",\"requestPath\":\"/api/users\",\"currentRate\":25,\"limitRate\":20,\"timestamp\":\"%s\"}",
            Instant.now().toString()
        );
    }
}