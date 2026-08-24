package com.adil.cvscanner.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DistributedRateLimitStateIT {

    private static final int REDIS_PORT =
            6379;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse(
                            "redis:7-alpine"
                    )
            )
                    .withExposedPorts(
                            REDIS_PORT
                    );

    private RedisClient clientA;

    private RedisClient clientB;

    private StatefulRedisConnection<String, byte[]>
            connectionA;

    private StatefulRedisConnection<String, byte[]>
            connectionB;

    private LettuceBasedProxyManager<String>
            proxyManagerA;

    private LettuceBasedProxyManager<String>
            proxyManagerB;

    @BeforeEach
    void setUp() {

        String redisUri =
                "redis://"
                        + REDIS.getHost()
                        + ":"
                        + REDIS.getMappedPort(
                        REDIS_PORT
                );

        /*
         * ========================================================
         * INSTANCE A
         * ========================================================
         */

        RedisURI uriA =
                RedisURI.create(
                        redisUri
                );

        uriA.setTimeout(
                Duration.ofSeconds(
                        1
                )
        );

        clientA =
                RedisClient.create(
                        uriA
                );

        connectionA =
                clientA.connect(
                        RedisCodec.of(
                                StringCodec.UTF8,
                                ByteArrayCodec.INSTANCE
                        )
                );

        proxyManagerA =
                Bucket4jLettuce
                        .casBasedBuilder(
                                connectionA
                        )
                        .requestTimeout(
                                Duration.ofSeconds(
                                        1
                                )
                        )
                        .maxRetries(
                                5
                        )
                        .build();

        /*
         * ========================================================
         * INSTANCE B
         * ========================================================
         */

        RedisURI uriB =
                RedisURI.create(
                        redisUri
                );

        uriB.setTimeout(
                Duration.ofSeconds(
                        1
                )
        );

        clientB =
                RedisClient.create(
                        uriB
                );

        connectionB =
                clientB.connect(
                        RedisCodec.of(
                                StringCodec.UTF8,
                                ByteArrayCodec.INSTANCE
                        )
                );

        proxyManagerB =
                Bucket4jLettuce
                        .casBasedBuilder(
                                connectionB
                        )
                        .requestTimeout(
                                Duration.ofSeconds(
                                        1
                                )
                        )
                        .maxRetries(
                                5
                        )
                        .build();
    }

    @AfterEach
    void tearDown() {

        if (
                connectionA != null
        ) {

            connectionA.close();
        }

        if (
                connectionB != null
        ) {

            connectionB.close();
        }

        if (
                clientA != null
        ) {

            clientA.shutdown();
        }

        if (
                clientB != null
        ) {

            clientB.shutdown();
        }
    }

    /*
     * ============================================================
     * SHARED DISTRIBUTED STATE
     * ============================================================
     */

    @Test
    void shouldShareBucketStateAcrossIndependentInstances() {

        String key =
                "cvscanner:test:distributed:"
                        + UUID.randomUUID();

        BucketConfiguration configuration =
                createConfiguration();

        /*
         * Instance A öz proxy manager-i ilə bucket götürür.
         */
        Bucket bucketFromInstanceA =
                proxyManagerA.getProxy(
                        key,
                        () ->
                                configuration
                );

        /*
         * Instance B tamam ayrı Lettuce client və
         * connection ilə eyni Redis key-i götürür.
         */
        Bucket bucketFromInstanceB =
                proxyManagerB.getProxy(
                        key,
                        () ->
                                configuration
                );

        /*
         * Capacity = 2
         *
         * Instance A:
         *
         * 2 -> 1
         */
        boolean firstRequest =
                bucketFromInstanceA.tryConsume(
                        1
                );

        assertThat(
                firstRequest
        ).isTrue();

        /*
         * Instance B həmin shared bucket-i görməlidir:
         *
         * 1 -> 0
         */
        boolean secondRequest =
                bucketFromInstanceB.tryConsume(
                        1
                );

        assertThat(
                secondRequest
        ).isTrue();

        /*
         * Instance A yenidən request edir.
         *
         * Əgər state local olsaydı, burada token qalardı.
         *
         * Amma state Redis-də shared olduğu üçün:
         *
         * 0 token
         *
         * request rejected.
         */
        boolean thirdRequest =
                bucketFromInstanceA.tryConsume(
                        1
                );

        assertThat(
                thirdRequest
        ).isFalse();
    }

    /*
     * ============================================================
     * DIFFERENT KEYS MUST REMAIN INDEPENDENT
     * ============================================================
     */

    @Test
    void shouldKeepDifferentDistributedKeysIndependent() {

        BucketConfiguration configuration =
                createConfiguration();

        String keyA =
                "cvscanner:test:distributed:a:"
                        + UUID.randomUUID();

        String keyB =
                "cvscanner:test:distributed:b:"
                        + UUID.randomUUID();

        Bucket bucketA =
                proxyManagerA.getProxy(
                        keyA,
                        () ->
                                configuration
                );

        Bucket bucketB =
                proxyManagerB.getProxy(
                        keyB,
                        () ->
                                configuration
                );

        /*
         * Bucket A:
         *
         * 2 -> 1 -> 0
         */
        assertThat(
                bucketA.tryConsume(
                        1
                )
        ).isTrue();

        assertThat(
                bucketA.tryConsume(
                        1
                )
        ).isTrue();

        assertThat(
                bucketA.tryConsume(
                        1
                )
        ).isFalse();

        /*
         * Bucket B tamam başqa Redis key-dir.
         *
         * Ona görə hələ full capacity ilə başlamalıdır.
         */
        assertThat(
                bucketB.tryConsume(
                        1
                )
        ).isTrue();

        assertThat(
                bucketB.tryConsume(
                        1
                )
        ).isTrue();

        assertThat(
                bucketB.tryConsume(
                        1
                )
        ).isFalse();
    }

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     */

    private BucketConfiguration createConfiguration() {

        return BucketConfiguration
                .builder()
                .addLimit(
                        bandwidth ->
                                bandwidth
                                        .capacity(
                                                2
                                        )
                                        .refillGreedy(
                                                2,
                                                Duration.ofHours(
                                                        1
                                                )
                                        )
                )
                .build();
    }
}