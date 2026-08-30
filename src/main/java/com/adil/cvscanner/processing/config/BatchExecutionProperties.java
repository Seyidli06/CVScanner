package com.adil.cvscanner.processing.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.batch")
public record BatchExecutionProperties(

        @Min(1)
        int corePoolSize,

        @Min(1)
        int maxPoolSize,

        @Min(0)
        int queueCapacity,

        @Min(1)
        int awaitTerminationSeconds

) {

    @AssertTrue(
            message = "app.batch.max-pool-size must be greater than or equal to core-pool-size"
    )
    public boolean isPoolConfigurationValid() {
        return maxPoolSize >= corePoolSize;
    }
}
