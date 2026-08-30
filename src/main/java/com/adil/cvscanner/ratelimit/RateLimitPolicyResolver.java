package com.adil.cvscanner.ratelimit;

import org.springframework.http.HttpMethod;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class RateLimitPolicyResolver {

    private static final String UPLOADS_PATH =
            "/api/v1/uploads";

    private static final String CANDIDATES_PATH =
            "/api/v1/candidates";

    private static final String CSV_EXPORT_PATH =
            "/api/v1/candidates/export.csv";

    private static final String XLSX_EXPORT_PATH =
            "/api/v1/candidates/export.xlsx";

    private static final Pattern UPLOAD_STATUS_PATTERN =
            Pattern.compile(
                    "^/api/v1/uploads/[^/]+$"
            );

    private static final Pattern PROCESSING_FAILURES_PATTERN =
            Pattern.compile(
                    "^/api/v1/uploads/[^/]+/failures$"
            );

    public Optional<RateLimitPolicy> resolve(
            String method,
            String path
    ) {

        Objects.requireNonNull(
                method,
                "method must not be null"
        );

        Objects.requireNonNull(
                path,
                "path must not be null"
        );

        if (
                HttpMethod.POST.matches(
                        method
                )
                        &&
                        UPLOADS_PATH.equals(
                                path
                        )
        ) {

            return Optional.of(
                    RateLimitPolicy.UPLOAD
            );
        }

        if (
                !HttpMethod.GET.matches(
                        method
                )
        ) {

            return Optional.empty();
        }

        if (
                CSV_EXPORT_PATH.equals(
                        path
                )
                        ||
                        XLSX_EXPORT_PATH.equals(
                                path
                        )
        ) {

            return Optional.of(
                    RateLimitPolicy.EXPORT
            );
        }

        if (
                CANDIDATES_PATH.equals(
                        path
                )
        ) {

            return Optional.of(
                    RateLimitPolicy.READ
            );
        }

        if (
                UPLOAD_STATUS_PATTERN
                        .matcher(
                                path
                        )
                        .matches()
        ) {

            return Optional.of(
                    RateLimitPolicy.READ
            );
        }

        if (
                PROCESSING_FAILURES_PATTERN
                        .matcher(
                                path
                        )
                        .matches()
        ) {

            return Optional.of(
                    RateLimitPolicy.READ
            );
        }

        return Optional.empty();
    }
}
