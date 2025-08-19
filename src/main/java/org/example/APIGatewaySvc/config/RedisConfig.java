package org.example.APIGatewaySvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 캐시 및 세션 저장소 설정 클래스
 * MSA 환경에서 성능 향상을 위한 Redis 기반 캐시 시스템 구성
 * 
 * 주요 기능:
 * - Redis 연결 팩토리 및 커넥션 풀 설정
 * - RedisTemplate 구성 (JSON 직렬화/역직렬화)
 * - Spring Cache 추상화를 위한 캐시 매니저 설정
 * - TTL 및 캐시 정책 관리
 */
@Configuration
@EnableCaching
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "redis.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * Redis Connection Factory 설정
     * Spring Cloud Gateway Rate Limiter가 사용하는 기본 연결 팩토리
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        // 필요시 패스워드 설정
        // config.setPassword("your-password");
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setEagerInitialization(true);
        return factory;
    }

    /**
     * Reactive Redis Connection Factory 설정
     * Rate Limiter 전용 Reactive 연결 팩토리
     */
    @Bean("reactiveRedisConnectionFactory")
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        // 필요시 패스워드 설정
        // config.setPassword("your-password");
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setEagerInitialization(true);
        return factory;
    }

//    @Bean
//    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, Object> template = new RedisTemplate<>();
//        template.setConnectionFactory(connectionFactory);
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
//        template.setHashKeySerializer(new StringRedisSerializer());
//        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
//        return template;
//    }

    // 비동기 Reactive RedisTemplate 설정
    @Bean
    @Primary
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate() {
        ReactiveRedisConnectionFactory factory = reactiveRedisConnectionFactory();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();
        return new ReactiveStringRedisTemplate(factory, context);
    }

    /**
     * RedisTemplate for general use
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}