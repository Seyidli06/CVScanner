package com.adil.cvscanner.processing.batch;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(
        prefix = "app.batch.retry"
)
public record CvProcessingRetryProperties(


        @Min(0)
        long maxRetries,


        @NotNull
        Duration delay

) {

    @AssertTrue(
            message =
                    "app.batch.retry.delay must not be negative"
    )
    public boolean isDelayValid() {

        return delay != null
                && !delay.isNegative();
    }
}