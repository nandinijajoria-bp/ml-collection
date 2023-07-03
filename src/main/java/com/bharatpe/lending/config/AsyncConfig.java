package com.bharatpe.lending.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Value("${piramal.core.pool.size:25}")
    int piramalAsyncCorePoolSize;
    @Value("${piramal.max.pool.size:70}")
    int piramalAsyncMaxPoolSize;

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(100);
        executor.setThreadNamePrefix("LendingAsyncExecutor-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "piramalPoolTaskExecutor")
    public Executor piramalAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(piramalAsyncCorePoolSize);
        executor.setMaxPoolSize(piramalAsyncMaxPoolSize);
        executor.setThreadNamePrefix("PiramalAsyncThread::");
        executor.initialize();
        return executor;
    }
}
