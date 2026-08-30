package com.adil.cvscanner.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Configuration(
        proxyBeanMethods = false
)
@ConditionalOnProperty(
        prefix = "app.rate-limit",
        name = "enabled",
        havingValue = "true"
)
public class RateLimitRedisConfiguration {

    @Bean(
            destroyMethod = "shutdown"
    )
    RedisClient rateLimitRedisClient(
            RateLimitProperties properties
    ) {

        RedisURI redisUri =
                RedisURI.create(
                        properties.getRedisUri()
                );

        redisUri.setTimeout(
                properties.getRedisTimeout()
        );

        return RedisClient.create(
                redisUri
        );
    }

    @Bean(
            destroyMethod = "close"
    )
    @Lazy
    StatefulRedisConnection<String, byte[]>
    rateLimitRedisConnection(
            RedisClient rateLimitRedisClient
    ) {

        return rateLimitRedisClient.connect(
                RedisCodec.of(
                        StringCodec.UTF8,
                        ByteArrayCodec.INSTANCE
                )
        );
    }

    @Bean
    @Lazy
    LettuceBasedProxyManager<String>
    rateLimitProxyManager(
            StatefulRedisConnection<String, byte[]>
                    rateLimitRedisConnection,
            RateLimitProperties properties
    ) {

        Duration keepAfterRefill =
                longestRefillPeriod(
                        properties
                );

        return Bucket4jLettuce
                .casBasedBuilder(
                        rateLimitRedisConnection
                )
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(
                                        keepAfterRefill
                                )
                )
                .requestTimeout(
                        properties.getRedisTimeout()
                )
                .maxRetries(
                        5
                )
                .build();
    }

    @Bean
    RateLimitPolicyResolver rateLimitPolicyResolver() {

        return new RateLimitPolicyResolver();
    }

    @Bean
    RateLimitResponseWriter rateLimitResponseWriter(
            JsonMapper jsonMapper
    ) {

        return new RateLimitResponseWriter(
                jsonMapper
        );
    }

    @Bean
    RateLimitingFilter rateLimitingFilter(
            RateLimitPolicyResolver policyResolver,
            RateLimitService rateLimitService,
            RateLimitResponseWriter responseWriter
    ) {

        return new RateLimitingFilter(
                policyResolver,
                rateLimitService,
                responseWriter
        );
    }

    @Bean
    FilterRegistrationBean<RateLimitingFilter>
    rateLimitingFilterRegistration(
            RateLimitingFilter rateLimitingFilter
    ) {

        FilterRegistrationBean<RateLimitingFilter>
                registration =
                new FilterRegistrationBean<>(
                        rateLimitingFilter
                );

        registration.setEnabled(
                false
        );

        return registration;
    }

    private Duration longestRefillPeriod(
            RateLimitProperties properties
    ) {

        Duration longest =
                properties
                        .getUpload()
                        .getRefillPeriod();

        if (
                properties
                        .getRead()
                        .getRefillPeriod()
                        .compareTo(
                                longest
                        ) > 0
        ) {

            longest =
                    properties
                            .getRead()
                            .getRefillPeriod();
        }

        if (
                properties
                        .getExport()
                        .getRefillPeriod()
                        .compareTo(
                                longest
                        ) > 0
        ) {

            longest =
                    properties
                            .getExport()
                            .getRefillPeriod();
        }

        return longest;
    }
}
