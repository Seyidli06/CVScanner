package com.adil.cvscanner.ratelimit;

public enum RateLimitPolicy {

    UPLOAD(
            "upload"
    ),

    READ(
            "read"
    ),

    EXPORT(
            "export"
    );

    private final String keySegment;

    RateLimitPolicy(
            String keySegment
    ) {

        this.keySegment = keySegment;
    }

    public String getKeySegment() {
        return keySegment;
    }
}