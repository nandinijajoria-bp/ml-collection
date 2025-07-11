package com.bharatpe.lending.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.TaskExecutorBuilder;
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

    @Value("${merchant.account.info.async.core.pool.size:5}")
    private int merchantAccountInfoPoolSize;
    @Value("${merchant.account.info.async.max.pool.size:10}")
    private int merchantAccountInfoMaxPoolSize;
    @Value("${merchant.account.info.async.queue.size:25}")
    private int merchantAccountQueueSize;

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
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "lenderPoolTaskExecutor")
    public Executor lendeAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(piramalAsyncCorePoolSize);
        executor.setMaxPoolSize(piramalAsyncMaxPoolSize);
        executor.setThreadNamePrefix("LenderAsyncThread::");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "emiDashboardTaskExecutor")
    public Executor getEmiDashboardTaskExecutor(){
        return new TaskExecutorBuilder()
                .threadNamePrefix("emi-dashboard-task-executor-")
                .corePoolSize(25)
                .maxPoolSize(50)
                .queueCapacity(250)
                .build();
    }

    @Bean(name = "accountInfoTaskExecutor")
    public Executor getAccountInfoTaskExecutor(){
        return new TaskExecutorBuilder()
                .threadNamePrefix("account-info-task-executor-")
                .corePoolSize(merchantAccountInfoPoolSize)
                .maxPoolSize(merchantAccountInfoMaxPoolSize)
                .queueCapacity(merchantAccountQueueSize)
                .build();
    }

    @Bean(name = "commonAsyncTaskExecutor")
    public Executor getCommonAsyncTaskExecutor(){
        return new TaskExecutorBuilder()
                .threadNamePrefix("common-async-task-executor-")
                .corePoolSize(15)
                .build();
    }


}
