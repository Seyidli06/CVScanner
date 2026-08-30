package com.adil.cvscanner.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(
        prefix = "app.rate-limit"
)
public class RateLimitProperties {

    private boolean enabled = true;

    @NotBlank
    private String redisUri;

    @NotNull
    private Duration redisTimeout;

    @NotBlank
    private String keyPrefix;

    private boolean failOpen;

    @Valid
    @NotNull
    private Limit upload = new Limit();

    @Valid
    @NotNull
    private Limit read = new Limit();

    @Valid
    @NotNull
    private Limit export = new Limit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;
    }

    public String getRedisUri() {
        return redisUri;
    }

    public void setRedisUri(
            String redisUri
    ) {
        this.redisUri = redisUri;
    }

    public Duration getRedisTimeout() {
        return redisTimeout;
    }

    public void setRedisTimeout(
            Duration redisTimeout
    ) {
        this.redisTimeout = redisTimeout;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(
            String keyPrefix
    ) {
        this.keyPrefix = keyPrefix;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(
            boolean failOpen
    ) {
        this.failOpen = failOpen;
    }

    public Limit getUpload() {
        return upload;
    }

    public void setUpload(
            Limit upload
    ) {
        this.upload = upload;
    }

    public Limit getRead() {
        return read;
    }

    public void setRead(
            Limit read
    ) {
        this.read = read;
    }

    public Limit getExport() {
        return export;
    }

    public void setExport(
            Limit export
    ) {
        this.export = export;
    }

    public static class Limit {

        @Positive
        private long capacity;

        @Positive
        private long refillTokens;

        @NotNull
        private Duration refillPeriod;

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(
                long capacity
        ) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(
                long refillTokens
        ) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(
                Duration refillPeriod
        ) {
            this.refillPeriod = refillPeriod;
        }
    }
}
