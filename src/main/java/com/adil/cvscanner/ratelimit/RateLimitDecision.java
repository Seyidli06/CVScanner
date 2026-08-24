package com.adil.cvscanner.ratelimit;

import java.time.Duration;
import java.util.Objects;

public record RateLimitDecision(
        boolean allowed,
        boolean backendAvailable,
        long remainingTokens,
        Duration retryAfter
) {

    public RateLimitDecision {

        if (remainingTokens < 0) {

            throw new IllegalArgumentException(
                    "remainingTokens must not be negative"
            );
        }

        Objects.requireNonNull(
                retryAfter,
                "retryAfter must not be null"
        );

        if (retryAfter.isNegative()) {

            throw new IllegalArgumentException(
                    "retryAfter must not be negative"
            );
        }
    }

    public static RateLimitDecision allowed(
            long remainingTokens
    ) {

        return new RateLimitDecision(
                true,
                true,
                remainingTokens,
                Duration.ZERO
        );
    }

    public static RateLimitDecision rateLimited(
            long remainingTokens,
            Duration retryAfter
    ) {

        return new RateLimitDecision(
                false,
                true,
                remainingTokens,
                retryAfter
        );
    }



    public static RateLimitDecision backendUnavailable(
            boolean failOpen
    ) {

        return new RateLimitDecision(
                failOpen,
                false,
                0,
                Duration.ZERO
        );
    }
}