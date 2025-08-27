package org.example.APIGatewaySvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.example.APIGatewaySvc.service.GatewayLogService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auth0 OAuth2 로그인 처리를 위한 컨트롤러
 * 
 * *** 주석 처리됨 - User Service로 Auth0 로그인 처리 이전 ***
 * Auth0 로그인 로직이 User Service로 이전되었습니다.
 * 롤백이 필요한 경우 아래 주석을 해제하세요.
 * 
 * 마이그레이션 완료 후 이 파일을 삭제할 수 있습니다.
 */
// @RestController
// @RequestMapping("/auth")
// @Tag(name = "Authentication", description = "Auth0 OAuth2 인증 관련 API")
public class AuthController {

    /*
     * *** 모든 필드와 메서드들이 주석 처리됨 ***
     * User Service의 AuthController로 이전되었습니다.
     * 
     * 주요 이전된 기능들:
     * - Auth0 로그인 시작 (/auth/login)
     * - 로그인 성공 처리 (/auth/login-success)
     * - 로그아웃 처리 (/auth/logout)
     * - 사용자 정보 조회 (/auth/user-info)
     * - Auth0 설정 정보 제공 (/auth/config)
     * - JWT 토큰 블랙리스트 관리
     * 
     * 새로운 엔드포인트는 User Service에서 접근하세요:
     * - GET http://localhost:8081/api/auth/login
     * - GET http://localhost:8081/api/auth/login-success
     * - POST http://localhost:8081/api/auth/logout
     * - GET http://localhost:8081/api/auth/user-info
     * - GET http://localhost:8081/api/auth/config
     */

    // private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // @Value("${auth0.issuerUri}")
    // private String issuerUri;

    // @Value("${auth0.client-id:}")
    // private String clientId;

    // @Value("${auth0.audience}")
    // private String audience;

    // @Value("${auth0.logout-redirect-uri:http://localhost:8080/auth/logout-success}")
    // private String logoutRedirectUri;

    // private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    // private final GatewayLogService gatewayLogService;
    // private final ReactiveRedisTemplate<String, String> redisTemplate;

    // public AuthController(ReactiveOAuth2AuthorizedClientService authorizedClientService,
    //                      @org.springframework.beans.factory.annotation.Autowired(required = false) GatewayLogService gatewayLogService,
    //                      @org.springframework.beans.factory.annotation.Autowired(required = false) ReactiveRedisTemplate<String, String> redisTemplate) {
    //     this.authorizedClientService = authorizedClientService;
    //     this.gatewayLogService = gatewayLogService;
    //     this.redisTemplate = redisTemplate;
    // }

    /*
     * 모든 메서드들은 User Service의 AuthController로 이전되었습니다.
     * 상세 구현은 마이그레이션 가이드 문서를 참조하세요: AUTH_MIGRATION_GUIDE.md
     */

}