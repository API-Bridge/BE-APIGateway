package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Block Management", description = "사용자/IP/API키 차단 관리 API")
@RestController
@RequestMapping("/internal/block")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class InternalBlockController {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public InternalBlockController(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Operation(
        summary = "사용자/IP/API키 차단"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "차단 성공", 
            content = @Content(mediaType = "application/json", 
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "message": "Successfully blocked user: user123",
                      "type": "user",
                      "id": "user123",
                      "reason": "Multiple failed login attempts",
                      "expiresAt": "2024-01-15T10:30:00Z"
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 타입", 
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": false,
                      "error": "Invalid block type. Must be one of: user, ip, key"
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "500", description = "차단 실패")
    })
    @PostMapping("/{type}")
    public Mono<ResponseEntity<Map<String, Object>>> block(
        @Parameter(description = "차단 타입 (user, ip, key)", example = "user", required = true) 
        @PathVariable String type,
        
        @Parameter(description = "차단할 대상 ID", example = "user123", required = true)
        @RequestParam String id,
        
        @Parameter(description = "TTL 초 단위 (미지정시 영구차단)", example = "3600")
        @RequestParam(required = false) Long ttlSeconds,
        
        @Parameter(description = "차단 사유", example = "Multiple failed login attempts")
        @RequestParam(required = false) String reason) {
        
        if (!isValidType(type)) {
            return Mono.just(ResponseEntity.badRequest().body(createErrorResponse("Invalid block type. Must be one of: user, ip, key")));
        }
        
        String key = "blocked:" + type + ":" + id;
        String blockValue = reason != null ? reason : "Blocked by admin";
        
        // Redis에 키 설정 (TTL과 함께)
        Mono<Boolean> setResult;
        if (ttlSeconds != null && ttlSeconds > 0) {
            setResult = redisTemplate.opsForValue().set(key, blockValue, Duration.ofSeconds(ttlSeconds));
        } else {
            setResult = redisTemplate.opsForValue().set(key, blockValue);
        }
        
        return setResult.map(success -> {
            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", String.format("Successfully blocked %s: %s", type, id));
                response.put("type", type);
                response.put("id", id);
                response.put("reason", blockValue);
                if (ttlSeconds != null && ttlSeconds > 0) {
                    response.put("expiresAt", Instant.now().plusSeconds(ttlSeconds).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse("Failed to block"));
            }
        });
    }

    @Operation(summary = "차단 해제", description = "지정된 타입과 ID의 차단을 해제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "차단 해제 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 타입"),
        @ApiResponse(responseCode = "404", description = "차단 정보 없음")
    })
    @DeleteMapping("/{type}/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> unblock(
        @Parameter(description = "차단 타입", example = "user") @PathVariable String type,
        @Parameter(description = "해제할 대상 ID", example = "user123") @PathVariable String id) {
        if (!isValidType(type)) {
            return Mono.just(ResponseEntity.badRequest().body(createErrorResponse("Invalid unblock type. Must be one of: user, ip, key")));
        }
        
        String key = "blocked:" + type + ":" + id;
        
        return redisTemplate.delete(key).map(deleted -> {
            if (deleted > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", String.format("Successfully unblocked %s: %s", type, id));
                response.put("type", type);
                response.put("id", id);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("Block not found: " + id));
            }
        });
    }

    @Operation(summary = "차단 목록 조회", description = "지정된 타입의 모든 차단 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "success": true,
                      "type": "user",
                      "count": 2,
                      "blocked": [
                        {
                          "id": "user123",
                          "reason": "Failed login attempts",
                          "expiresAt": "2024-01-15T10:30:00Z"
                        }
                      ]
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 타입")
    })
    @GetMapping("/{type}")
    public Mono<ResponseEntity<Map<String, Object>>> listBlocked(
        @Parameter(description = "차단 타입", example = "user") @PathVariable String type) {
        if (!isValidType(type)) {
            return Mono.just(ResponseEntity.badRequest().body(createErrorResponse("Invalid type. Must be one of: user, ip, key")));
        }
        
        String pattern = "blocked:" + type + ":*";
        
        return redisTemplate.keys(pattern)
            .flatMap(key -> 
                redisTemplate.opsForValue().get(key)
                    .zipWith(redisTemplate.getExpire(key))
                    .map(tuple -> {
                        Map<String, Object> blockInfo = new HashMap<>();
                        String id = key.substring(key.lastIndexOf(":") + 1);
                        blockInfo.put("id", id);
                        blockInfo.put("reason", tuple.getT1());
                        Duration ttl = tuple.getT2();
                        if (!ttl.isNegative() && !ttl.isZero()) {
                            blockInfo.put("expiresAt", Instant.now().plus(ttl).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        } else {
                            blockInfo.put("expiresAt", null);
                        }
                        return blockInfo;
                    })
            )
            .collectList()
            .map(blockedList -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("type", type);
                response.put("blocked", blockedList);
                response.put("count", blockedList.size());
                return ResponseEntity.ok(response);
            });
    }

    @Operation(summary = "차단 상태 확인", description = "특정 대상의 차단 상태를 확인합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "차단된 경우", value = """
                        {
                          "success": true,
                          "blocked": true,
                          "type": "user",
                          "id": "user123",
                          "reason": "Failed login attempts",
                          "expiresAt": "2024-01-15T10:30:00Z"
                        }
                        """),
                    @ExampleObject(name = "차단되지 않은 경우", value = """
                        {
                          "success": true,
                          "blocked": false,
                          "type": "user",
                          "id": "user123"
                        }
                        """)
                }
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 타입")
    })
    @GetMapping("/{type}/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> checkBlocked(
        @Parameter(description = "차단 타입", example = "user") @PathVariable String type,
        @Parameter(description = "확인할 대상 ID", example = "user123") @PathVariable String id) {
        if (!isValidType(type)) {
            return Mono.just(ResponseEntity.badRequest().body(createErrorResponse("Invalid type. Must be one of: user, ip, key")));
        }
        
        String key = "blocked:" + type + ":" + id;
        
        return redisTemplate.hasKey(key)
            .flatMap(exists -> {
                if (!exists) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("blocked", false);
                    response.put("type", type);
                    response.put("id", id);
                    return Mono.just(ResponseEntity.ok(response));
                }
                
                return redisTemplate.opsForValue().get(key)
                    .zipWith(redisTemplate.getExpire(key))
                    .map(tuple -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("blocked", true);
                        response.put("type", type);
                        response.put("id", id);
                        response.put("reason", tuple.getT1());
                        Duration ttl = tuple.getT2();
                        if (!ttl.isNegative() && !ttl.isZero()) {
                            response.put("expiresAt", Instant.now().plus(ttl).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        } else {
                            response.put("expiresAt", null);
                        }
                        return ResponseEntity.ok(response);
                    });
            });
    }

    private boolean isValidType(String type) {
        return "user".equals(type) || "ip".equals(type) || "key".equals(type);
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}