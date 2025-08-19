package org.example.APIGatewaySvc.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring Cloud LoadBalancer 설정 클래스
 * 
 * Eureka 서비스 디스커버리와 함께 동작하여 마이크로서비스 간의 로드 밸런싱을 제공합니다.
 * 각 서비스별로 다른 로드 밸런싱 전략을 적용할 수 있습니다.
 * 
 * 지원하는 로드 밸런싱 전략:
 * - Round Robin (기본값)
 * - Random
 * - Weighted Response Time
 * - Custom Health-based
 */
@Configuration
public class LoadBalancerConfig {

    /**
     * User Service용 로드 밸런서 설정
     * 사용자 관련 요청의 특성을 고려하여 Round Robin 전략 사용
     * 
     * @param environment Spring Environment
     * @param loadBalancerClientFactory LoadBalancer Client Factory
     * @return ReactorLoadBalancer<ServiceInstance> 로드 밸런서
     */
    @Bean
    public ReactorLoadBalancer<ServiceInstance> userServiceLoadBalancer(
            Environment environment, 
            LoadBalancerClientFactory loadBalancerClientFactory) {
        
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        
        // USER-SERVICE에 대해서만 적용
        if ("USER-SERVICE".equals(name)) {
            return new RandomLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name
            );
        }
        
        // 기본 Round Robin 사용
        return null;
    }

    // Mission Service 연동 전이므로 주석 처리
    // TODO: Mission Service 연동 후 주석 해제
    /*
    @Bean
    public ReactorLoadBalancer<ServiceInstance> missionServiceLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        
        // MISSION-SERVICE에 대해서만 적용
        if ("MISSION-SERVICE".equals(name)) {
            // 향후 WeightedResponseTimeLoadBalancer 등으로 변경 가능
            return new RandomLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name
            );
        }
        
        return null;
    }
    */
}
