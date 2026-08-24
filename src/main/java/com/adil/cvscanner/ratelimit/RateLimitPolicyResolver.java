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

    /*
     * ============================================================
     * RESOLVE POLICY
     * ============================================================
     *
     * Rate limiting yalnız bizim real application
     * API contract-a tətbiq olunur.
     *
     * Health, metrics, unknown routes və unsupported
     * HTTP method-lar burada policy almır.
     *
     * SecurityFilterChain onların authorization
     * qərarını ayrıca verir.
     */

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

        /*
         * ========================================================
         * UPLOAD
         * ========================================================
         */

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

        /*
         * ========================================================
         * NON-GET REQUESTS
         * ========================================================
         *
         * Application-da qalan rate-limited endpoint-lərin
         * hamısı GET-dir.
         */

        if (
                !HttpMethod.GET.matches(
                        method
                )
        ) {

            return Optional.empty();
        }

        /*
         * ========================================================
         * EXPORT
         * ========================================================
         *
         * Export READ-dən əvvəl yoxlanılır.
         *
         * Beləliklə:
         *
         * /api/v1/candidates/export.csv
         * /api/v1/candidates/export.xlsx
         *
         * normal READ bucket-ə düşmür.
         */

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

        /*
         * ========================================================
         * CANDIDATE SEARCH
         * ========================================================
         */

        if (
                CANDIDATES_PATH.equals(
                        path
                )
        ) {

            return Optional.of(
                    RateLimitPolicy.READ
            );
        }

        /*
         * ========================================================
         * UPLOAD STATUS
         * ========================================================
         *
         * GET /api/v1/uploads/{uploadId}
         */

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

        /*
         * ========================================================
         * PROCESSING FAILURES
         * ========================================================
         *
         * GET
         * /api/v1/uploads/{uploadId}/failures
         */

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