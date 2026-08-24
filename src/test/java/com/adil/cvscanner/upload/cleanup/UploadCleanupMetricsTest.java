package com.adil.cvscanner.upload.cleanup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class UploadCleanupMetricsTest {

    private SimpleMeterRegistry
            meterRegistry;

    private UploadCleanupMetrics
            metrics;

    @BeforeEach
    void setUp() {

        meterRegistry =
                new SimpleMeterRegistry();

        metrics =
                new UploadCleanupMetrics(
                        meterRegistry
                );
    }

    /*
     * ============================================================
     * TEST 1
     * SUCCESSFUL CLEANUP
     * ============================================================
     */

    @Test
    void shouldRecordSuccessfulCleanupRun() {

        UploadCleanupRunResult result =
                new UploadCleanupRunResult(
                        5,
                        3,
                        2,
                        0
                );

        metrics.recordCompletedRun(
                result,
                Duration.ofMillis(
                        250
                )
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.RUNS
                )
        ).isEqualTo(
                1.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.SELECTED
                )
        ).isEqualTo(
                5.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.DELETED
                )
        ).isEqualTo(
                3.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.ALREADY_ABSENT
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.ITEM_FAILURES
                )
        ).isZero();

        assertThat(
                counter(
                        UploadCleanupMetrics.RUN_FAILURES
                )
        ).isZero();

        /*
         * Timer:
         *
         * 1 recorded execution.
         */

        assertThat(
                meterRegistry
                        .get(
                                UploadCleanupMetrics.DURATION
                        )
                        .timer()
                        .count()
        ).isEqualTo(
                1
        );

        assertThat(
                meterRegistry
                        .get(
                                UploadCleanupMetrics.DURATION
                        )
                        .timer()
                        .totalTime(
                                java.util.concurrent.TimeUnit.MILLISECONDS
                        )
        ).isEqualTo(
                250.0
        );

        /*
         * Zero item failure olduğuna görə
         * last-success timestamp set olunmalıdır.
         */

        assertThat(
                gauge(
                        UploadCleanupMetrics.LAST_SUCCESS
                )
        ).isGreaterThan(
                0.0
        );
    }

    /*
     * ============================================================
     * TEST 2
     * PARTIAL CLEANUP
     * ============================================================
     *
     * run itself completed,
     * amma bəzi item-lər failed-dir.
     */

    @Test
    void shouldRecordPartialRunWithoutUpdatingLastSuccess() {

        UploadCleanupRunResult result =
                new UploadCleanupRunResult(
                        4,
                        2,
                        1,
                        1
                );

        metrics.recordCompletedRun(
                result,
                Duration.ofSeconds(
                        1
                )
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.RUNS
                )
        ).isEqualTo(
                1.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.SELECTED
                )
        ).isEqualTo(
                4.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.DELETED
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.ALREADY_ABSENT
                )
        ).isEqualTo(
                1.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.ITEM_FAILURES
                )
        ).isEqualTo(
                1.0
        );

        /*
         * Partial run:
         *
         * last successful timestamp dəyişmir.
         */

        assertThat(
                gauge(
                        UploadCleanupMetrics.LAST_SUCCESS
                )
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 3
     * WHOLE RUN FAILURE
     * ============================================================
     */

    @Test
    void shouldRecordWholeRunFailure() {

        metrics.recordRunFailure(
                Duration.ofMillis(
                        500
                )
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.RUN_FAILURES
                )
        ).isEqualTo(
                1.0
        );

        /*
         * Failed run normal completed-runs counter-a
         * daxil edilmir.
         */

        assertThat(
                counter(
                        UploadCleanupMetrics.RUNS
                )
        ).isZero();

        assertThat(
                meterRegistry
                        .get(
                                UploadCleanupMetrics.DURATION
                        )
                        .timer()
                        .count()
        ).isEqualTo(
                1
        );

        assertThat(
                gauge(
                        UploadCleanupMetrics.LAST_SUCCESS
                )
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 4
     * METRICS ACCUMULATE
     * ============================================================
     */

    @Test
    void shouldAccumulateMetricsAcrossRuns() {

        metrics.recordCompletedRun(
                new UploadCleanupRunResult(
                        3,
                        3,
                        0,
                        0
                ),
                Duration.ofMillis(
                        100
                )
        );

        metrics.recordCompletedRun(
                new UploadCleanupRunResult(
                        5,
                        2,
                        2,
                        1
                ),
                Duration.ofMillis(
                        200
                )
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.RUNS
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.SELECTED
                )
        ).isEqualTo(
                8.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.DELETED
                )
        ).isEqualTo(
                5.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.ALREADY_ABSENT
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.ITEM_FAILURES
                )
        ).isEqualTo(
                1.0
        );

        assertThat(
                meterRegistry
                        .get(
                                UploadCleanupMetrics.DURATION
                        )
                        .timer()
                        .count()
        ).isEqualTo(
                2
        );
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private double counter(
            String metricName
    ) {

        return meterRegistry
                .get(
                        metricName
                )
                .counter()
                .count();
    }

    private double gauge(
            String metricName
    ) {

        return meterRegistry
                .get(
                        metricName
                )
                .gauge()
                .value();
    }
}