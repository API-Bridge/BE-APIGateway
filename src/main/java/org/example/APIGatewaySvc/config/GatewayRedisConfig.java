package org.example.APIGatewaySvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

/**
 * Spring Cloud Gateway Rate Limiter를 위한 전용 Redis 설정
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "gateway.redis.config.enabled", havingValue = "false", matchIfMissing = false)
public class GatewayRedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void init() {
        log.info("Gateway Redis 설정 - Host: {}, Port: {}", redisHost, redisPort);
    }

    /**
     * Spring Cloud Gateway Rate Limiter 전용 Redis Connection Factory
     */
    @Bean("gatewayRedisConnectionFactory")
    public ReactiveRedisConnectionFactory gatewayRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setEagerInitialization(true);
        
        log.info("Gateway Redis Connection Factory 생성됨 - {}:{}", redisHost, redisPort);
        return factory;
    }

    /**
     * Spring Cloud Gateway Rate Limiter 전용 Redis Template
     */
    @Bean("gatewayRedisTemplate")
    public ReactiveRedisTemplate<String, String> gatewayRedisTemplate() {
        ReactiveRedisConnectionFactory connectionFactory = gatewayRedisConnectionFactory();
        
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext(new StringRedisSerializer())
                .hashKey(new StringRedisSerializer())
                .hashValue(new StringRedisSerializer())
                .build();
        
        ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
        
        log.info("Gateway Redis Template 생성됨");
        return template;
    }

    /**
     * Rate Limiter 스크립트 실행을 위한 RedisTemplate
     * Spring Cloud Gateway RedisRateLimiter가 이 빈을 찾아 사용함
     */
    @Bean("redisTemplate")
    public ReactiveRedisTemplate<String, String> redisTemplate() {
        return gatewayRedisTemplate();
    }
}