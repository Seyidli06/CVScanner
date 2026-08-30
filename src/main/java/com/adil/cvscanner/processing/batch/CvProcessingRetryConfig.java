package com.adil.cvscanner.processing.batch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.TransientDataAccessException;

@Configuration(
        proxyBeanMethods = false
)
@EnableConfigurationProperties(
        CvProcessingRetryProperties.class
)
public class CvProcessingRetryConfig {

    @Bean("cvProcessingRetryPolicy")
    public RetryPolicy cvProcessingRetryPolicy(
            CvProcessingRetryProperties properties
    ) {

        return RetryPolicy
                .builder()

                .includes(
                        TransientDataAccessException.class
                )

                .maxRetries(
                        properties.maxRetries()
                )

                .delay(
                        properties.delay()
                )

                .build();
    }
}
