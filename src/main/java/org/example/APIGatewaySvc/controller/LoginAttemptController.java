package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.APIGatewaySvc.service.LoginAttemptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Login Attempt Monitoring", description = "로그인 시도 모니터링 API")
@RestController
@RequestMapping("/internal/login-attempts")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class LoginAttemptController {
    
    private final LoginAttemptService loginAttemptService;
    
    public LoginAttemptController(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }
    
    @Operation(
        summary = "사용자 로그인 시도 통계 조회",
        description = """
            사용자의 현재 로그인 시도 통계를 조회합니다.
            
            ### 응답 정보
            - **currentAttempts**: 현재 실패 횟수
            - **remainingAttempts**: 남은 시도 횟수
            - **windowExpiry**: 시도 윈도우 만료 시간
            - **blocked**: 차단 여부
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "userId": "user123",
                      "currentAttempts": 2,
                      "remainingAttempts": 3,
                      "maxAttempts": 5,
                      "windowExpiry": "2024-01-15T10:30:00Z",
                      "blocked": false
                    }
                    """)
            )
        )
    })
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> getUserAttemptStats(
        @Parameter(description = "사용자 ID", example = "user123") 
        @PathVariable String userId) {
        
        return loginAttemptService.getLoginAttemptStats(userId)
            .map(stats -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("userId", stats.getUserId());
                response.put("currentAttempts", stats.getCurrentAttempts());
                response.put("remainingAttempts", stats.getRemainingAttempts());
                response.put("maxAttempts", 5);
                response.put("windowExpiry", stats.getWindowExpiry());
                response.put("blocked", stats.isBlocked());
                return ResponseEntity.ok(response);
            });
    }
    
    @Operation(
        summary = "IP별 로그인 시도 횟수 조회",
        description = "특정 IP의 현재 로그인 시도 횟수를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "ipAddress": "192.168.1.100",
                      "currentAttempts": 3,
                      "maxAttempts": 10
                    }
                    """)
            )
        )
    })
    @GetMapping("/ip/{ipAddress}")
    public Mono<ResponseEntity<Map<String, Object>>> getIpAttemptStats(
        @Parameter(description = "IP 주소", example = "192.168.1.100") 
        @PathVariable String ipAddress) {
        
        return loginAttemptService.getIpAttemptCount(ipAddress)
            .map(attempts -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("ipAddress", ipAddress);
                response.put("currentAttempts", attempts);
                response.put("maxAttempts", 10);
                response.put("remainingAttempts", Math.max(0, 10 - attempts));
                return ResponseEntity.ok(response);
            });
    }
    
    @Operation(
        summary = "로그인 시도 횟수 초기화",
        description = """
            사용자의 로그인 시도 횟수를 수동으로 초기화합니다.
            관리자가 사용자의 차단을 해제하거나 시도 횟수를 리셋할 때 사용합니다.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "초기화 성공"),
        @ApiResponse(responseCode = "404", description = "시도 기록 없음")
    })
    @DeleteMapping("/user/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> resetUserAttempts(
        @Parameter(description = "사용자 ID", example = "user123") 
        @PathVariable String userId) {
        
        return loginAttemptService.recordLoginSuccess(userId)
            .then(Mono.fromCallable(() -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "로그인 시도 횟수가 초기화되었습니다");
                response.put("userId", userId);
                return ResponseEntity.ok(response);
            }));
    }
}