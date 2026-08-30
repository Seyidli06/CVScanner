package com.adil.cvscanner.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyResolverTest {

    private RateLimitPolicyResolver resolver;

    @BeforeEach
    void setUp() {

        resolver =
                new RateLimitPolicyResolver();
    }

    





    @Test
    void shouldResolveUploadPostAsUploadPolicy() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "POST",
                        "/api/v1/uploads"
                );

        assertThat(
                result
        )
                .contains(
                        RateLimitPolicy.UPLOAD
                );
    }

    





    @Test
    void shouldResolveCandidateSearchAsReadPolicy() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/candidates"
                );

        assertThat(
                result
        )
                .contains(
                        RateLimitPolicy.READ
                );
    }

    





    @Test
    void shouldResolveUploadStatusAsReadPolicy() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/uploads/8e0fc670-0868-4ed5-9e06-8cbb3c92beaa"
                );

        assertThat(
                result
        )
                .contains(
                        RateLimitPolicy.READ
                );
    }

    





    @Test
    void shouldResolveProcessingFailuresAsReadPolicy() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/uploads/8e0fc670-0868-4ed5-9e06-8cbb3c92beaa/failures"
                );

        assertThat(
                result
        )
                .contains(
                        RateLimitPolicy.READ
                );
    }

    





    @Test
    void shouldResolveCsvExportAsExportPolicy() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/candidates/export.csv"
                );

        assertThat(
                result
        )
                .contains(
                        RateLimitPolicy.EXPORT
                );
    }

    





    @Test
    void shouldResolveXlsxExportAsExportPolicy() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/candidates/export.xlsx"
                );

        assertThat(
                result
        )
                .contains(
                        RateLimitPolicy.EXPORT
                );
    }

    





    @Test
    void shouldIgnoreHealthEndpoint() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/actuator/health"
                );

        assertThat(
                result
        ).isEmpty();
    }

    @Test
    void shouldIgnoreLivezEndpoint() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/livez"
                );

        assertThat(
                result
        ).isEmpty();
    }

    





    @Test
    void shouldIgnoreMetricsEndpoint() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/actuator/metrics"
                );

        assertThat(
                result
        ).isEmpty();
    }

    





    @Test
    void shouldIgnoreUnknownApplicationRoute() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/unknown"
                );

        assertThat(
                result
        ).isEmpty();
    }

    





    @Test
    void shouldIgnoreUnsupportedCandidatePost() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "POST",
                        "/api/v1/candidates"
                );

        assertThat(
                result
        ).isEmpty();
    }

    @Test
    void shouldIgnoreUploadDelete() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "DELETE",
                        "/api/v1/uploads/8e0fc670-0868-4ed5-9e06-8cbb3c92beaa"
                );

        assertThat(
                result
        ).isEmpty();
    }

    





    @Test
    void shouldNotTreatNestedUnknownUploadRouteAsRead() {

        Optional<RateLimitPolicy> result =
                resolver.resolve(
                        "GET",
                        "/api/v1/uploads/123/unknown"
                );

        assertThat(
                result
        ).isEmpty();
    }
}