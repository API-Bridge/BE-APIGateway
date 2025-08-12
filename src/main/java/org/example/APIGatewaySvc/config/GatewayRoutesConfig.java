package org.example.APIGatewaySvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route examples for reference. Configuration is primarily in YAML profiles.
 */

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "gateway.routes.programmatic.enabled", havingValue = "true", matchIfMissing = false)
public class GatewayRoutesConfig {

    @Value("${services.user-service.uri:http://localhost:8081}")
    private String userServiceUri;

    @Value("${services.api-management-service.uri:http://localhost:8082}")
    private String apiManagementServiceUri;

    @Bean
    public RouteLocator programmaticRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("users-programmatic", r -> r.path("/users/**")
                        .filters(f -> f.stripPrefix(1)
                                .circuitBreaker(c -> c.setName("userSvcCb").setFallbackUri("forward:/fallback/user-service")))
                        .uri(userServiceUri))
                .route("apimgmt-programmatic", r -> r.path("/apimgmt/**")
                        .filters(f -> f.stripPrefix(1)
                                .circuitBreaker(c -> c.setName("apiMgmtSvcCb").setFallbackUri("forward:/fallback/api-management")))
                        .uri(apiManagementServiceUri))
                .build();
    }
}


