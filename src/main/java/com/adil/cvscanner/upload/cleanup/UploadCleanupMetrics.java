package com.adil.cvscanner.upload.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UploadCleanupMetrics {

    /*
     * ============================================================
     * CLEANUP RUN METRICS
     * ============================================================
     */

    public static final String RUNS =
            "cvscanner.cleanup.runs";

    public static final String SELECTED =
            "cvscanner.cleanup.items.selected";

    public static final String DELETED =
            "cvscanner.cleanup.items.deleted";

    public static final String ALREADY_ABSENT =
            "cvscanner.cleanup.items.already_absent";

    public static final String ITEM_FAILURES =
            "cvscanner.cleanup.items.failed";

    public static final String RUN_FAILURES =
            "cvscanner.cleanup.run_failures";

    public static final String DURATION =
            "cvscanner.cleanup.duration";

    public static final String LAST_SUCCESS =
            "cvscanner.cleanup.last_success_epoch_seconds";

    /*
     * ============================================================
     * DISTRIBUTED LOCK METRICS
     * ============================================================
     */

    public static final String LOCK_ACQUIRED =
            "cvscanner.cleanup.lock.acquired";

    public static final String LOCK_CONTENDED =
            "cvscanner.cleanup.lock.contended";

    /*
     * ============================================================
     * SCHEDULER METRICS
     * ============================================================
     */

    public static final String SCHEDULER_ATTEMPTS =
            "cvscanner.cleanup.scheduler.attempts";

    public static final String SCHEDULER_LOCAL_SKIPS =
            "cvscanner.cleanup.scheduler.local_skips";

    public static final String SCHEDULER_FAILURES =
            "cvscanner.cleanup.scheduler.failures";

    /*
     * ============================================================
     * METERS
     * ============================================================
     */

    private final Counter runs;

    private final Counter selected;

    private final Counter deleted;

    private final Counter alreadyAbsent;

    private final Counter itemFailures;

    private final Counter runFailures;

    private final Counter lockAcquired;

    private final Counter lockContended;

    private final Counter schedulerAttempts;

    private final Counter schedulerLocalSkips;

    private final Counter schedulerFailures;

    private final Timer duration;

    /*
     * 0:
     *
     * application start-dan sonra hələ tam uğurlu
     * cleanup execution olmayıb.
     */
    private final AtomicLong lastSuccessEpochSeconds =
            new AtomicLong(
                    0
            );

    public UploadCleanupMetrics(
            MeterRegistry meterRegistry
    ) {

        Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
        );

        /*
         * ========================================================
         * CLEANUP RUN
         * ========================================================
         */

        this.runs =
                Counter.builder(
                                RUNS
                        )
                        .description(
                                "Number of completed upload storage cleanup runs"
                        )
                        .register(
                                meterRegistry
                        );

        this.selected =
                Counter.builder(
                                SELECTED
                        )
                        .description(
                                "Number of uploads selected for storage cleanup"
                        )
                        .register(
                                meterRegistry
                        );

        this.deleted =
                Counter.builder(
                                DELETED
                        )
                        .description(
                                "Number of upload storage directories physically deleted"
                        )
                        .register(
                                meterRegistry
                        );

        this.alreadyAbsent =
                Counter.builder(
                                ALREADY_ABSENT
                        )
                        .description(
                                "Number of cleanup candidates whose storage was already absent"
                        )
                        .register(
                                meterRegistry
                        );

        this.itemFailures =
                Counter.builder(
                                ITEM_FAILURES
                        )
                        .description(
                                "Number of individual upload cleanup failures"
                        )
                        .register(
                                meterRegistry
                        );

        this.runFailures =
                Counter.builder(
                                RUN_FAILURES
                        )
                        .description(
                                "Number of cleanup executions that failed before returning a result"
                        )
                        .register(
                                meterRegistry
                        );

        this.duration =
                Timer.builder(
                                DURATION
                        )
                        .description(
                                "Upload storage cleanup execution duration"
                        )
                        .register(
                                meterRegistry
                        );

        Gauge.builder(
                        LAST_SUCCESS,
                        lastSuccessEpochSeconds,
                        AtomicLong::get
                )
                .description(
                        "Epoch second of the last cleanup run completed with zero item failures"
                )
                .register(
                        meterRegistry
                );

        /*
         * ========================================================
         * DISTRIBUTED LOCK
         * ========================================================
         */

        this.lockAcquired =
                Counter.builder(
                                LOCK_ACQUIRED
                        )
                        .description(
                                "Number of PostgreSQL cleanup advisory locks successfully acquired"
                        )
                        .register(
                                meterRegistry
                        );

        this.lockContended =
                Counter.builder(
                                LOCK_CONTENDED
                        )
                        .description(
                                "Number of cleanup executions skipped because another instance held the PostgreSQL lock"
                        )
                        .register(
                                meterRegistry
                        );

        /*
         * ========================================================
         * SCHEDULER
         * ========================================================
         */

        this.schedulerAttempts =
                Counter.builder(
                                SCHEDULER_ATTEMPTS
                        )
                        .description(
                                "Number of enabled scheduled cleanup invocation attempts"
                        )
                        .register(
                                meterRegistry
                        );

        this.schedulerLocalSkips =
                Counter.builder(
                                SCHEDULER_LOCAL_SKIPS
                        )
                        .description(
                                "Number of scheduled cleanup invocations skipped because a cleanup was already running in the same JVM"
                        )
                        .register(
                                meterRegistry
                        );

        this.schedulerFailures =
                Counter.builder(
                                SCHEDULER_FAILURES
                        )
                        .description(
                                "Number of scheduled cleanup executions that failed at the scheduler boundary"
                        )
                        .register(
                                meterRegistry
                        );
    }

    /*
     * ============================================================
     * CLEANUP RUN COMPLETED
     * ============================================================
     */

    public void recordCompletedRun(
            UploadCleanupRunResult result,
            Duration executionDuration
    ) {

        Objects.requireNonNull(
                result,
                "result must not be null"
        );

        validateDuration(
                executionDuration
        );

        runs.increment();

        increment(
                selected,
                result.selected()
        );

        increment(
                deleted,
                result.deleted()
        );

        increment(
                alreadyAbsent,
                result.alreadyAbsent()
        );

        increment(
                itemFailures,
                result.failed()
        );

        duration.record(
                executionDuration
        );

        /*
         * Yalnız heç bir item failure yoxdursa
         * "last successful cleanup" yenilənir.
         */

        if (
                result.failed() == 0
        ) {

            lastSuccessEpochSeconds.set(
                    Instant.now()
                            .getEpochSecond()
            );
        }
    }

    /*
     * ============================================================
     * WHOLE CLEANUP RUN FAILURE
     * ============================================================
     */

    public void recordRunFailure(
            Duration executionDuration
    ) {

        validateDuration(
                executionDuration
        );

        runFailures.increment();

        duration.record(
                executionDuration
        );
    }

    /*
     * ============================================================
     * DISTRIBUTED LOCK
     * ============================================================
     */

    public void recordDistributedLockAcquired() {

        lockAcquired.increment();
    }

    public void recordDistributedLockContended() {

        lockContended.increment();
    }

    /*
     * ============================================================
     * SCHEDULER
     * ============================================================
     */

    public void recordSchedulerAttempt() {

        schedulerAttempts.increment();
    }

    public void recordSchedulerLocalSkip() {

        schedulerLocalSkips.increment();
    }

    public void recordSchedulerFailure() {

        schedulerFailures.increment();
    }

    /*
     * ============================================================
     * COUNTER HELPER
     * ============================================================
     */

    private void increment(
            Counter counter,
            int amount
    ) {

        if (
                amount < 0
        ) {

            throw new IllegalArgumentException(
                    "metric increment amount must not be negative"
            );
        }

        if (
                amount > 0
        ) {

            counter.increment(
                    amount
            );
        }
    }

    /*
     * ============================================================
     * DURATION VALIDATION
     * ============================================================
     */

    private void validateDuration(
            Duration executionDuration
    ) {

        Objects.requireNonNull(
                executionDuration,
                "executionDuration must not be null"
        );

        if (
                executionDuration.isNegative()
        ) {

            throw new IllegalArgumentException(
                    "executionDuration must not be negative"
            );
        }
    }
}