package org.example.APIGatewaySvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 작업 처리를 위한 설정 클래스
 * Spring 비동기 메소드 실행 및 ThreadPool 관리 설정
 * 
 * 주요 기능:
 * - @Async 애노테이션 활성화
 * - 비동기 작업용 ThreadPool 구성
 * - 마이크로서비스 환경에서 성능 최적화를 위한 쓰레드 설정
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 비동기 작업용 ThreadPool 실행자 구성
     * MSA 환경에서 효율적인 리소스 관리를 위한 설정
     * 
     * @return Executor 비동기 작업 실행자
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 기본 쓰레드 풀 크기: 일반적인 비동기 작업 처리용
        executor.setCorePoolSize(10);
        
        // 최대 쓰레드 풀 크기: 피크 시간대 대비
        executor.setMaxPoolSize(20);
        
        // 대기열 용량: 작업 대기열의 최대 크기
        executor.setQueueCapacity(500);
        
        // 쓰레드 이름 접두사: 디버깅 및 모니터링용
        executor.setThreadNamePrefix("custom-api-async-");
        
        // 애플리케이션 종료 시 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 종료 대기 시간 (초)
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}