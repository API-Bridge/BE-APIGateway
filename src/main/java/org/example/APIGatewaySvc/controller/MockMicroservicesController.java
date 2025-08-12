package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/mock")
@Tag(name = "Mock Microservices", 
     description = "마이크로서비스 목업 API - 실제 마이크로서비스가 개발되기 전까지 테스트용으로 사용")
public class MockMicroservicesController {

    private final Random random = new Random();

    // =========================== User Service Mock ===========================

    @GetMapping("/users/profile")
    @Operation(
        summary = "사용자 프로필 조회 (Mock)", 
        description = """
            **User Service 목업 API**
            
            실제 사용자 서비스의 프로필 조회 API를 시뮬레이션합니다.
            - JWT 토큰에서 사용자 정보 추출
            - Rate Limit: 20req/s (관대한 정책)
            - Circuit Breaker: userSvcCb
            
            **실제 라우팅**: `/gateway/users/profile` → 이 엔드포인트로 라우팅됨
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "프로필 조회 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "userId": "auth0|123456789",
                          "email": "user@example.com",
                          "name": "홍길동",
                          "avatar": "https://example.com/avatar.jpg",
                          "createdAt": "2024-01-01T00:00:00Z",
                          "lastLoginAt": "2024-01-15T10:30:00Z",
                          "role": "USER"
                        }
                        """
                )
            )
        )
    })
    public Mono<ResponseEntity<Map<String, Object>>> getUserProfile(
            @Parameter(hidden = true) Authentication authentication) {
        
        return Mono.fromCallable(() -> {
            Map<String, Object> profile = new HashMap<>();
            
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                profile.put("userId", jwt.getSubject());
                profile.put("email", jwt.getClaimAsString("email"));
                profile.put("name", jwt.getClaimAsString("name"));
                profile.put("avatar", "https://example.com/avatar/" + jwt.getSubject() + ".jpg");
            } else {
                profile.put("userId", "anonymous");
                profile.put("email", "anonymous@example.com");
                profile.put("name", "익명 사용자");
            }
            
            profile.put("createdAt", "2024-01-01T00:00:00Z");
            profile.put("lastLoginAt", Instant.now().toString());
            profile.put("role", "USER");
            profile.put("mockService", "UserService");
            
            return ResponseEntity.ok(profile);
        });
    }

    @PostMapping("/users/profile")
    @Operation(
        summary = "사용자 프로필 업데이트 (Mock)",
        description = "사용자 프로필 정보를 업데이트하는 목업 API"
    )
    public Mono<ResponseEntity<Map<String, Object>>> updateUserProfile(
            @RequestBody Map<String, Object> profileData,
            Authentication authentication) {
        
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "프로필이 성공적으로 업데이트되었습니다");
            response.put("updatedFields", profileData.keySet());
            response.put("updatedAt", Instant.now().toString());
            response.put("mockService", "UserService");
            
            return ResponseEntity.ok(response);
        });
    }

    // =========================== API Management Service Mock ===========================

    @GetMapping("/apimgmt/apis")
    @Operation(
        summary = "API 목록 조회 (Mock)",
        description = """
            **API Management Service 목업 API**
            
            - Rate Limit: 15req/s (관리 정책)
            - Circuit Breaker: apiMgmtSvcCb
            
            **실제 라우팅**: `/gateway/apimgmt/apis` → 이 엔드포인트로 라우팅됨
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> getApiList() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            
            List<Map<String, Object>> apis = List.of(
                Map.of("id", "api-1", "name", "User API", "version", "v1.0", "status", "ACTIVE"),
                Map.of("id", "api-2", "name", "Payment API", "version", "v2.1", "status", "ACTIVE"),
                Map.of("id", "api-3", "name", "Notification API", "version", "v1.5", "status", "DEPRECATED")
            );
            
            response.put("apis", apis);
            response.put("total", apis.size());
            response.put("mockService", "APIManagementService");
            
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/apimgmt/apis")
    @Operation(
        summary = "API 등록 (Mock)",
        description = "새로운 API를 등록하는 목업 API"
    )
    public Mono<ResponseEntity<Map<String, Object>>> createApi(@RequestBody Map<String, Object> apiData) {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("id", "api-" + random.nextInt(1000));
            response.put("message", "API가 성공적으로 등록되었습니다");
            response.put("data", apiData);
            response.put("createdAt", Instant.now().toString());
            response.put("mockService", "APIManagementService");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        });
    }

    // =========================== AI Feature Service Mock ===========================

    @PostMapping("/aifeature/chat")
    @Operation(
        summary = "AI 채팅 (Mock)",
        description = """
            **AI Feature Service 목업 API**
            
            - Rate Limit: 5req/s, 2토큰/요청 (엄격한 정책)
            - Circuit Breaker: aiFeatureSvcCb
            - 느린 호출 임계값: 5초
            
            **실제 라우팅**: `/gateway/aifeature/chat` → 이 엔드포인트로 라우팅됨
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> aiChat(@RequestBody Map<String, Object> chatRequest) {
        return Mono.fromCallable(() -> {
            // AI 서비스 시뮬레이션을 위한 지연
            try {
                Thread.sleep(1000 + random.nextInt(2000)); // 1-3초 랜덤 지연
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Map<String, Object> response = new HashMap<>();
            String userMessage = (String) chatRequest.get("message");
            
            List<String> aiResponses = List.of(
                "안녕하세요! 무엇을 도와드릴까요?",
                "흥미로운 질문이네요. 더 자세히 설명해 주시겠어요?",
                "네, 이해했습니다. 다음과 같이 도움을 드릴 수 있어요...",
                "죄송하지만 현재 서비스 처리 중입니다. 잠시 후 다시 시도해 주세요."
            );
            
            response.put("userMessage", userMessage);
            response.put("aiResponse", aiResponses.get(random.nextInt(aiResponses.size())));
            response.put("conversationId", "conv-" + random.nextInt(10000));
            response.put("processingTime", "2.5s");
            response.put("mockService", "AIFeatureService");
            
            return ResponseEntity.ok(response);
        });
    }

    @GetMapping("/aifeature/models")
    @Operation(
        summary = "AI 모델 목록 조회 (Mock)",
        description = "사용 가능한 AI 모델 목록을 조회하는 목업 API"
    )
    public Mono<ResponseEntity<Map<String, Object>>> getAiModels() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            
            List<Map<String, Object>> models = List.of(
                Map.of("id", "gpt-4", "name", "GPT-4", "type", "CHAT", "status", "AVAILABLE"),
                Map.of("id", "claude-3", "name", "Claude-3", "type", "CHAT", "status", "AVAILABLE"),
                Map.of("id", "dall-e-3", "name", "DALL-E 3", "type", "IMAGE", "status", "MAINTENANCE")
            );
            
            response.put("models", models);
            response.put("total", models.size());
            response.put("mockService", "AIFeatureService");
            
            return ResponseEntity.ok(response);
        });
    }

    // =========================== System Management Service Mock ===========================

    @GetMapping("/sysmgmt/config")
    @Operation(
        summary = "시스템 설정 조회 (Mock)",
        description = """
            **System Management Service 목업 API**
            
            - Rate Limit: 15req/s (관리 정책)
            - Circuit Breaker: systemMgmtSvcCb
            
            **실제 라우팅**: `/gateway/sysmgmt/config` → 이 엔드포인트로 라우팅됨
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> getSystemConfig() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            
            Map<String, Object> config = Map.of(
                "maintenance", false,
                "maxUsers", 10000,
                "features", Map.of(
                    "aiChat", true,
                    "apiManagement", true,
                    "userRegistration", true
                ),
                "version", "1.0.0"
            );
            
            response.put("config", config);
            response.put("lastUpdated", "2024-01-15T08:00:00Z");
            response.put("mockService", "SystemManagementService");
            
            return ResponseEntity.ok(response);
        });
    }

    // =========================== Error Simulation ===========================

    @GetMapping("/users/error/{statusCode}")
    @Operation(
        summary = "에러 시뮬레이션 (Mock)",
        description = "테스트를 위한 다양한 HTTP 에러 상태 코드 생성"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Bad Request 시뮬레이션"),
        @ApiResponse(responseCode = "404", description = "Not Found 시뮬레이션"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error 시뮬레이션"),
        @ApiResponse(responseCode = "503", description = "Service Unavailable 시뮬레이션")
    })
    public Mono<ResponseEntity<Map<String, Object>>> simulateError(
            @PathVariable @Parameter(description = "시뮬레이션할 HTTP 상태 코드") int statusCode) {
        
        return Mono.fromCallable(() -> {
            Map<String, Object> errorResponse = new HashMap<>();
            
            switch (statusCode) {
                case 400 -> {
                    errorResponse.put("error", "Bad Request");
                    errorResponse.put("message", "잘못된 요청입니다");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }
                case 404 -> {
                    errorResponse.put("error", "Not Found");
                    errorResponse.put("message", "리소스를 찾을 수 없습니다");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                case 500 -> {
                    errorResponse.put("error", "Internal Server Error");
                    errorResponse.put("message", "서버 내부 오류가 발생했습니다");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
                case 503 -> {
                    errorResponse.put("error", "Service Unavailable");
                    errorResponse.put("message", "서비스를 일시적으로 사용할 수 없습니다");
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
                }
                default -> {
                    errorResponse.put("message", "정상 응답 (에러 시뮬레이션 아님)");
                    return ResponseEntity.ok(errorResponse);
                }
            }
        });
    }

    // =========================== Circuit Breaker Test ===========================

    @GetMapping("/users/slow")
    @Operation(
        summary = "느린 응답 시뮬레이션 (Mock)",
        description = """
            서킷브레이커 테스트를 위한 느린 응답 시뮬레이션
            
            - 3-8초의 랜덤 지연 시간
            - slowCallDurationThreshold(3초) 초과 시 느린 호출로 판정
            - 연속으로 호출하면 서킷브레이커가 동작할 수 있음
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> slowResponse() {
        return Mono.fromCallable(() -> {
            // 느린 응답 시뮬레이션 (3-8초)
            int delay = 3000 + random.nextInt(5000);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "느린 응답 완료");
            response.put("actualDelay", delay + "ms");
            response.put("mockService", "UserService");
            
            return ResponseEntity.ok(response);
        });
    }

    @GetMapping("/users/random-fail")
    @Operation(
        summary = "랜덤 실패 시뮬레이션 (Mock)",
        description = """
            서킷브레이커 테스트를 위한 랜덤 실패 시뮬레이션
            
            - 50% 확률로 500 에러 반환
            - failureRateThreshold(50%) 초과 시 서킷브레이커 OPEN
            - 연속으로 호출하여 서킷브레이커 동작 테스트 가능
            """
    )
    public Mono<ResponseEntity<Map<String, Object>>> randomFail() {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            
            if (random.nextBoolean()) {
                // 50% 확률로 성공
                response.put("message", "성공!");
                response.put("mockService", "UserService");
                return ResponseEntity.ok(response);
            } else {
                // 50% 확률로 실패
                response.put("error", "Internal Server Error");
                response.put("message", "랜덤 실패 시뮬레이션");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        });
    }
}