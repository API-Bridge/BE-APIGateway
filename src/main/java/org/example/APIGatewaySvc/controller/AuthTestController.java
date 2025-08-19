package org.example.APIGatewaySvc.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.dto.StandardResponseDTO;
import org.example.APIGatewaySvc.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 테스트 컨트롤러
 * JWT 토큰 인증 테스트 및 Kafka 로깅 테스트용
 */
@Slf4j
@RestController
@RequestMapping("/test/auth")
public class AuthTestController {

    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;

    /**
     * JWT 토큰이 필요한 보호된 엔드포인트 (인증 로깅 테스트용)
     */
    @GetMapping("/protected")
    public ResponseEntity<StandardResponseDTO> protectedEndpoint() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "JWT 토큰 인증이 성공했습니다");
        data.put("timestamp", LocalDateTime.now());
        data.put("access", "authorized");

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("보호된 리소스에 성공적으로 접근했습니다")
                .data(data)
                .build();

        log.info("보호된 엔드포인트 접근 성공");
        return ResponseEntity.ok(response);
    }

    /**
     * 공개 엔드포인트 (JWT 토큰 불필요)
     */
    @GetMapping("/public")
    public ResponseEntity<StandardResponseDTO> publicEndpoint() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "공개 엔드포인트입니다");
        data.put("timestamp", LocalDateTime.now());
        data.put("access", "public");

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("공개 리소스에 접근했습니다")
                .data(data)
                .build();

        log.info("공개 엔드포인트 접근");
        return ResponseEntity.ok(response);
    }

    /**
     * 수동으로 인증 이벤트를 Kafka로 전송 (테스트용)
     */
    @PostMapping("/send-test-auth-event")
    public ResponseEntity<StandardResponseDTO> sendTestAuthEvent() {
        try {
            if (kafkaProducerService != null) {
                Map<String, Object> testAuthEvent = new HashMap<>();
                testAuthEvent.put("eventType", "TEST_AUTH");
                testAuthEvent.put("path", "/test/auth/send-test-auth-event");
                testAuthEvent.put("method", "POST");
                testAuthEvent.put("userId", "test-user-123");
                testAuthEvent.put("clientIp", "127.0.0.1");
                testAuthEvent.put("userAgent", "TestClient/1.0");
                testAuthEvent.put("success", true);
                testAuthEvent.put("message", "수동 테스트 인증 이벤트");
                testAuthEvent.put("timestamp", LocalDateTime.now().toString());
                testAuthEvent.put("sessionId", "test-session-" + System.currentTimeMillis());
                testAuthEvent.put("requestId", "test-request-" + System.currentTimeMillis());

                kafkaProducerService.sendAuthEvent(testAuthEvent);

                Map<String, Object> responseData = new HashMap<>();
                responseData.put("status", "sent");
                responseData.put("topic", "events.auth");
                responseData.put("event", testAuthEvent);

                StandardResponseDTO response = StandardResponseDTO.builder()
                        .success(true)
                        .message("테스트 인증 이벤트를 Kafka로 전송했습니다")
                        .data(responseData)
                        .build();

                return ResponseEntity.ok(response);
            } else {
                StandardResponseDTO response = StandardResponseDTO.builder()
                        .success(false)
                        .message("KafkaProducerService를 사용할 수 없습니다")
                        .data(null)
                        .build();

                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("테스트 인증 이벤트 전송 실패: {}", e.getMessage(), e);

            StandardResponseDTO response = StandardResponseDTO.builder()
                    .success(false)
                    .message("테스트 인증 이벤트 전송에 실패했습니다: " + e.getMessage())
                    .data(null)
                    .build();

            return ResponseEntity.ok(response);
        }
    }
}