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

    /*
     * ============================================================
     * REDIS CLIENT
     * ============================================================
     *
     * RedisClient yaratmaq Redis server-ə connection açmır.
     */

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

    /*
     * ============================================================
     * REDIS CONNECTION
     * ============================================================
     *
     * @Lazy vacibdir.
     *
     * Redis application startup zamanı unavailable olsa belə
     * Spring context qalxa bilir.
     */

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

    /*
     * ============================================================
     * BUCKET4J DISTRIBUTED PROXY MANAGER
     * ============================================================
     */

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

    /*
     * ============================================================
     * POLICY RESOLVER
     * ============================================================
     */

    @Bean
    RateLimitPolicyResolver rateLimitPolicyResolver() {

        return new RateLimitPolicyResolver();
    }

    /*
     * ============================================================
     * RESPONSE WRITER
     * ============================================================
     */

    @Bean
    RateLimitResponseWriter rateLimitResponseWriter(
            JsonMapper jsonMapper
    ) {

        return new RateLimitResponseWriter(
                jsonMapper
        );
    }

    /*
     * ============================================================
     * RATE LIMIT FILTER
     * ============================================================
     */

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

    /*
     * ============================================================
     * DISABLE NORMAL SERVLET REGISTRATION
     * ============================================================
     *
     * Filter yalnız Spring Security chain daxilində işləməlidir.
     *
     * Əks halda eyni request iki dəfə filter-dən keçə bilər
     * və iki token consume edilə bilər.
     */

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

    /*
     * ============================================================
     * REDIS BUCKET EXPIRATION
     * ============================================================
     */

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