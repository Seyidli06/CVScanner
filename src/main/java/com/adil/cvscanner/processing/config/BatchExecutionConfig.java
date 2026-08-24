package com.adil.cvscanner.processing.config;

import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(
        BatchExecutionProperties.class
)
public class BatchExecutionConfig {

    @Bean
    @BatchTaskExecutor
    public ThreadPoolTaskExecutor cvBatchTaskExecutor(
            BatchExecutionProperties properties
    ) {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(
                properties.corePoolSize()
        );

        executor.setMaxPoolSize(
                properties.maxPoolSize()
        );

        executor.setQueueCapacity(
                properties.queueCapacity()
        );

        executor.setThreadNamePrefix(
                "cv-batch-"
        );

        executor.setWaitForTasksToCompleteOnShutdown(
                true
        );

        executor.setAwaitTerminationSeconds(
                properties.awaitTerminationSeconds()
        );

        executor.initialize();

        return executor;
    }
}