package org.example.APIGatewaySvc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 (Swagger) 문서화 설정 클래스
 * API 문서 자동 생성 및 Swagger UI 커스터마이징을 위한 설정
 * 
 * 주요 기능:
 * - API 문서 메타데이터 설정 (제목, 버전, 설명, 연락처 등)
 * - 다중 환경 서버 설정 (로컬, 개발, 운영)
 * - JWT Bearer 토큰 인증 스키마 설정
 * - OpenAPI 스펙 기반 자동 문서 생성
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("API Gateway")
                .version("1.0.0")
                .description("마이크로서비스 아키텍처를 위한 통합 API Gateway입니다. JWT 인증, 라우팅, Rate Limiting, Circuit Breaker 기능을 제공합니다.")
                .contact(new Contact()
                    .name("API Bridge Team")
                    .email("support@api-bridge.com")
                    .url("https://api-bridge.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                new Server().url("https://gateway.api-bridge.dev").description("개발 환경"),
                new Server().url("https://gateway.api-bridge.com").description("운영 환경")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT Authorization header using the Bearer scheme.")));
    }
}