package com.adil.cvscanner.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component(
        "rateLimitRedis"
)
public class RateLimitRedisHealthIndicator
        implements HealthIndicator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    RateLimitRedisHealthIndicator.class
            );

    private static final String PONG =
            "PONG";

    private final RateLimitProperties properties;

    private final ObjectProvider<RedisClient>
            redisClientProvider;

    public RateLimitRedisHealthIndicator(
            RateLimitProperties properties,
            ObjectProvider<RedisClient> redisClientProvider
    ) {

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.redisClientProvider =
                Objects.requireNonNull(
                        redisClientProvider,
                        "redisClientProvider must not be null"
                );
    }

    @Override
    public Health health() {

        /*
         * ========================================================
         * RATE LIMITING DISABLED
         * ========================================================
         *
         * Rate limiter söndürülübsə Redis application üçün
         * critical dependency deyil.
         */

        if (
                !properties.isEnabled()
        ) {

            return Health
                    .up()
                    .build();
        }

        /*
         * ========================================================
         * FAIL OPEN
         * ========================================================
         *
         * Redis unavailable olsa belə business request-lər
         * davam edə bildiyi üçün pod READY qala bilər.
         */

        if (
                properties.isFailOpen()
        ) {

            return Health
                    .up()
                    .build();
        }

        /*
         * ========================================================
         * FAIL CLOSED
         * ========================================================
         *
         * fail-open=false olduqda Redis rate-limited business
         * request-lər üçün critical dependency-dir.
         *
         * Health check üçün Bucket4j-in shared binary
         * StatefulRedisConnection bean-indən istifadə etmirik.
         *
         * RedisClient vasitəsilə ayrıca qısa connection açıb
         * PING göndəririk.
         */

        try {

            RedisClient redisClient =
                    redisClientProvider.getObject();

            try (
                    StatefulRedisConnection<String, String>
                            healthConnection =
                            redisClient.connect()
            ) {

                String response =
                        healthConnection
                                .sync()
                                .ping();

                if (
                        PONG.equalsIgnoreCase(
                                response
                        )
                ) {

                    return Health
                            .up()
                            .build();
                }

                LOGGER.warn(
                        "RATE_LIMIT_REDIS_HEALTH_CHECK_FAILED reason=unexpected-response"
                );

                return Health
                        .down()
                        .build();
            }

        } catch (
                RuntimeException exception
        ) {

            /*
             * Redis URI, host, credentials və başqa infrastructure
             * detalları health response və log-a yazılmır.
             */

            LOGGER.warn(
                    "RATE_LIMIT_REDIS_HEALTH_CHECK_FAILED errorType={}",
                    exception
                            .getClass()
                            .getSimpleName()
            );

            return Health
                    .down()
                    .build();
        }
    }
}