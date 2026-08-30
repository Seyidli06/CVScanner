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

        








        if (
                !properties.isEnabled()
        ) {

            return Health
                    .up()
                    .build();
        }

        








        if (
                properties.isFailOpen()
        ) {

            return Health
                    .up()
                    .build();
        }

        














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