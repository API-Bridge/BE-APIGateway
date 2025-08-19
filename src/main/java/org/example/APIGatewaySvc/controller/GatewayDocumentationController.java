package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.APIGatewaySvc.dto.StandardResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gateway 라우팅 엔드포인트 문서화용 컨트롤러
 * 실제로는 Spring Cloud Gateway가 라우팅을 처리하지만,
 * Swagger 문서화를 위해 엔드포인트들을 정의
 * 
 * 주의: 이 컨트롤러는 문서화 목적이며, 실제 /gateway/** 요청은
 * Spring Cloud Gateway에서 application.yml의 라우팅 설정에 따라 처리됩니다.
 */
@Slf4j
@RestController
@RequestMapping("/docs/gateway")
@Tag(name = "Gateway Routes Documentation", description = "마이크로서비스 라우팅 엔드포인트 문서화 (실제 경로: /gateway/**)")
@SecurityRequirement(name = "bearerAuth")
public class GatewayDocumentationController {

    @GetMapping("/users/**")
    @Operation(
        summary = "사용자 서비스 라우팅 (실제: /gateway/users/**)",
        description = "BE-UserService로 라우팅됩니다. JWT 토큰이 필수입니다. 실제 요청은 /gateway/users/** 경로로 해주세요.",
        responses = {
            @ApiResponse(responseCode = "200", description = "성공적으로 라우팅됨"),
            @ApiResponse(responseCode = "401", description = "JWT 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "Circuit Breaker 열림 - 서비스 사용 불가")
        }
    )
    public ResponseEntity<StandardResponseDTO> userServiceRoute(
            @Parameter(description = "사용자 서비스 하위 경로", example = "/profile") 
            @RequestParam(required = false) String path) {
        
        // 이 메소드는 실제로 호출되지 않음 - 문서화 목적만
        Map<String, Object> data = new HashMap<>();
        data.put("service", "BE-UserService");
        data.put("route", "/gateway/users/**");
        data.put("description", "이 엔드포인트는 BE-UserService로 라우팅됩니다");
        data.put("timestamp", LocalDateTime.now());

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("사용자 서비스로 라우팅됩니다")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/apimgmt/**")
    @Operation(
        summary = "API 관리 서비스 라우팅 (실제: /gateway/apimgmt/**)",
        description = "BE-APIManagementService로 라우팅됩니다. JWT 토큰이 필수입니다. 실제 요청은 /gateway/apimgmt/** 경로로 해주세요.",
        responses = {
            @ApiResponse(responseCode = "200", description = "성공적으로 라우팅됨"),
            @ApiResponse(responseCode = "401", description = "JWT 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "Circuit Breaker 열림 - 서비스 사용 불가")
        }
    )
    public ResponseEntity<StandardResponseDTO> apiManagementRoute() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "BE-APIManagementService");
        data.put("route", "/gateway/apimgmt/**");
        data.put("description", "이 엔드포인트는 BE-APIManagementService로 라우팅됩니다");
        data.put("timestamp", LocalDateTime.now());

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("API 관리 서비스로 라우팅됩니다")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/customapi/**")
    @Operation(
        summary = "커스텀 API 관리 서비스 라우팅 (실제: /gateway/customapi/**)",
        description = "BE-CustomAPIManagementService로 라우팅됩니다. JWT 토큰이 필수입니다. 실제 요청은 /gateway/customapi/** 경로로 해주세요.",
        responses = {
            @ApiResponse(responseCode = "200", description = "성공적으로 라우팅됨"),
            @ApiResponse(responseCode = "401", description = "JWT 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "Circuit Breaker 열림 - 서비스 사용 불가")
        }
    )
    public ResponseEntity<StandardResponseDTO> customApiManagementRoute() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "BE-CustomAPIManagementService");
        data.put("route", "/gateway/customapi/**");
        data.put("description", "이 엔드포인트는 BE-CustomAPIManagementService로 라우팅됩니다");
        data.put("timestamp", LocalDateTime.now());

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("커스텀 API 관리 서비스로 라우팅됩니다")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/aifeature/**")
    @Operation(
        summary = "AI 기능 서비스 라우팅 (실제: /gateway/aifeature/**)",
        description = "BE-AIFeatureService로 라우팅됩니다. JWT 토큰이 필수입니다. 실제 요청은 /gateway/aifeature/** 경로로 해주세요.",
        responses = {
            @ApiResponse(responseCode = "200", description = "성공적으로 라우팅됨"),
            @ApiResponse(responseCode = "401", description = "JWT 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "Circuit Breaker 열림 - 서비스 사용 불가")
        }
    )
    public ResponseEntity<StandardResponseDTO> aiFeatureRoute() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "BE-AIFeatureService");
        data.put("route", "/gateway/aifeature/**");
        data.put("description", "이 엔드포인트는 BE-AIFeatureService로 라우팅됩니다");
        data.put("timestamp", LocalDateTime.now());

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("AI 기능 서비스로 라우팅됩니다")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sysmgmt/**")
    @Operation(
        summary = "시스템 관리 서비스 라우팅 (실제: /gateway/sysmgmt/**)",
        description = "BE-SystemManagementService로 라우팅됩니다. JWT 토큰이 필수입니다. 실제 요청은 /gateway/sysmgmt/** 경로로 해주세요.",
        responses = {
            @ApiResponse(responseCode = "200", description = "성공적으로 라우팅됨"),
            @ApiResponse(responseCode = "401", description = "JWT 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "503", description = "Circuit Breaker 열림 - 서비스 사용 불가")
        }
    )
    public ResponseEntity<StandardResponseDTO> systemManagementRoute() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "BE-SystemManagementService");
        data.put("route", "/gateway/sysmgmt/**");
        data.put("description", "이 엔드포인트는 BE-SystemManagementService로 라우팅됩니다");
        data.put("timestamp", LocalDateTime.now());

        StandardResponseDTO response = StandardResponseDTO.builder()
                .success(true)
                .message("시스템 관리 서비스로 라우팅됩니다")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }
}