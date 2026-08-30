package com.adil.cvscanner.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

@Service
@ConditionalOnProperty(
        prefix = "app.rate-limit",
        name = "enabled",
        havingValue = "true"
)
public class RateLimitService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    RateLimitService.class
            );

    private static final long TOKENS_PER_REQUEST =
            1L;

    private final ObjectProvider<
            LettuceBasedProxyManager<String>
            > proxyManagerProvider;

    private final RateLimitProperties properties;

    public RateLimitService(
            ObjectProvider<
                    LettuceBasedProxyManager<String>
                    > proxyManagerProvider,
            RateLimitProperties properties
    ) {

        this.proxyManagerProvider =
                Objects.requireNonNull(
                        proxyManagerProvider,
                        "proxyManagerProvider must not be null"
                );

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );
    }

    public RateLimitDecision consume(
            RateLimitPolicy policy,
            String principal
    ) {

        Objects.requireNonNull(
                policy,
                "policy must not be null"
        );

        if (
                principal == null
                        || principal.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "principal must not be blank"
            );
        }

        String bucketKey =
                createBucketKey(
                        policy,
                        principal
                );

        try {

            LettuceBasedProxyManager<String>
                    proxyManager =
                    proxyManagerProvider.getObject();

            Bucket bucket =
                    proxyManager.getProxy(
                            bucketKey,
                            () ->
                                    createBucketConfiguration(
                                            policy
                                    )
                    );

            ConsumptionProbe probe =
                    bucket.tryConsumeAndReturnRemaining(
                            TOKENS_PER_REQUEST
                    );

            if (
                    probe.isConsumed()
            ) {

                return RateLimitDecision.allowed(
                        probe.getRemainingTokens()
                );
            }

            Duration retryAfter =
                    Duration.ofNanos(
                            Math.max(
                                    0L,
                                    probe
                                            .getNanosToWaitForRefill()
                            )
                    );

            return RateLimitDecision.rateLimited(
                    probe.getRemainingTokens(),
                    retryAfter
            );

        } catch (
                RuntimeException exception
        ) {

            LOGGER.warn(
                    "RATE_LIMIT_BACKEND_UNAVAILABLE policy={} failOpen={} errorType={}",
                    policy,
                    properties.isFailOpen(),
                    exception
                            .getClass()
                            .getSimpleName()
            );

            return RateLimitDecision
                    .backendUnavailable(
                            properties.isFailOpen()
                    );
        }
    }

    private BucketConfiguration createBucketConfiguration(
            RateLimitPolicy policy
    ) {

        RateLimitProperties.Limit limit =
                getLimit(
                        policy
                );

        return BucketConfiguration
                .builder()
                .addLimit(
                        bandwidth ->
                                bandwidth
                                        .capacity(
                                                limit
                                                        .getCapacity()
                                        )
                                        .refillGreedy(
                                                limit
                                                        .getRefillTokens(),
                                                limit
                                                        .getRefillPeriod()
                                        )
                )
                .build();
    }

    private RateLimitProperties.Limit getLimit(
            RateLimitPolicy policy
    ) {

        return switch (policy) {

            case UPLOAD ->
                    properties.getUpload();

            case READ ->
                    properties.getRead();

            case EXPORT ->
                    properties.getExport();
        };
    }

    private String createBucketKey(
            RateLimitPolicy policy,
            String principal
    ) {

        return properties.getKeyPrefix()
                + ":"
                + policy.getKeySegment()
                + ":"
                + hashPrincipal(
                principal
        );
    }

    private String hashPrincipal(
            String principal
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            principal.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            hash
                    );

        } catch (
                NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
}
