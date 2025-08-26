package org.example.APIGatewaySvc.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.mockito.Mockito;

/**
 * 간단한 테스트용 설정 클래스
 * 필수 Mock Bean들만 제공
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate() {
        ReactiveRedisTemplate<String, String> mockTemplate = Mockito.mock(ReactiveRedisTemplate.class);
        
        // 기본 Stub 설정
        Mockito.lenient().when(mockTemplate.opsForValue())
            .thenReturn(Mockito.mock(org.springframework.data.redis.core.ReactiveValueOperations.class));
        Mockito.lenient().when(mockTemplate.hasKey(Mockito.anyString()))
            .thenReturn(reactor.core.publisher.Mono.just(false));
        
        return mockTemplate;
    }

    @Bean
    @Primary
    public ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService() {
        return Mockito.mock(ReactiveOAuth2AuthorizedClientService.class);
    }

    @Bean
    @Primary
    public ReactiveClientRegistrationRepository reactiveClientRegistrationRepository() {
        return Mockito.mock(ReactiveClientRegistrationRepository.class);
    }
}