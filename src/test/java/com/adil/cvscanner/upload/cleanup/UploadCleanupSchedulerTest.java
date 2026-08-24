package com.adil.cvscanner.upload.cleanup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadCleanupSchedulerTest {

    private UploadCleanupProperties properties;

    private UploadCleanupExecutionCoordinator
            executionCoordinator;

    private SimpleMeterRegistry meterRegistry;

    private UploadCleanupMetrics cleanupMetrics;

    private UploadCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {

        properties =
                new UploadCleanupProperties();

        properties.setEnabled(
                true
        );

        properties.setSchedulerEnabled(
                true
        );

        properties.setCompletedRetention(
                Duration.ofDays(
                        7
                )
        );

        properties.setBatchSize(
                100
        );

        properties.setScheduleDelay(
                Duration.ofHours(
                        1
                )
        );

        properties.setInitialDelay(
                Duration.ofMinutes(
                        1
                )
        );

        executionCoordinator =
                mock(
                        UploadCleanupExecutionCoordinator.class
                );

        meterRegistry =
                new SimpleMeterRegistry();

        cleanupMetrics =
                new UploadCleanupMetrics(
                        meterRegistry
                );

        scheduler =
                new UploadCleanupScheduler(
                        properties,
                        executionCoordinator,
                        cleanupMetrics
                );
    }

    /*
     * ============================================================
     * TEST 1
     * SCHEDULER DISABLED
     * ============================================================
     */

    @Test
    void shouldNotRunOrRecordAttemptWhenSchedulerIsDisabled() {

        properties.setSchedulerEnabled(
                false
        );

        scheduler.runScheduledCleanup();

        verify(
                executionCoordinator,
                never()
        ).tryRunOnce();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 2
     * CLEANUP DISABLED
     * ============================================================
     */

    @Test
    void shouldNotRunOrRecordAttemptWhenCleanupIsDisabled() {

        properties.setEnabled(
                false
        );

        scheduler.runScheduledCleanup();

        verify(
                executionCoordinator,
                never()
        ).tryRunOnce();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 3
     * NORMAL EXECUTION
     * ============================================================
     */

    @Test
    void shouldRecordSchedulerAttemptForNormalRun() {

        when(
                executionCoordinator.tryRunOnce()
        ).thenReturn(
                UploadCleanupExecutionResult
                        .executed(
                                new UploadCleanupRunResult(
                                        3,
                                        2,
                                        1,
                                        0
                                )
                        )
        );

        scheduler.runScheduledCleanup();

        verify(
                executionCoordinator,
                times(
                        1
                )
        ).tryRunOnce();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isEqualTo(
                1.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_LOCAL_SKIPS
                )
        ).isZero();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_FAILURES
                )
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 4
     * DISTRIBUTED LOCK BUSY
     * ============================================================
     */

    @Test
    void shouldTreatDistributedLockContentionAsSafeSchedulerAttempt() {

        when(
                executionCoordinator.tryRunOnce()
        ).thenReturn(
                UploadCleanupExecutionResult
                        .skipped()
        );

        assertThatCode(
                scheduler::runScheduledCleanup
        ).doesNotThrowAnyException();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isEqualTo(
                1.0
        );

        /*
         * Distributed contention failure deyil.
         */

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_FAILURES
                )
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 5
     * EXECUTION FAILURE
     * ============================================================
     */

    @Test
    void shouldRecordFailureAndReleaseLocalGuard() {

        when(
                executionCoordinator.tryRunOnce()
        )
                .thenThrow(
                        new IllegalStateException(
                                "simulated"
                        )
                )
                .thenReturn(
                        UploadCleanupExecutionResult
                                .executed(
                                        new UploadCleanupRunResult(
                                                1,
                                                1,
                                                0,
                                                0
                                        )
                                )
                );

        assertThatCode(
                scheduler::runScheduledCleanup
        ).doesNotThrowAnyException();

        assertThatCode(
                scheduler::runScheduledCleanup
        ).doesNotThrowAnyException();

        verify(
                executionCoordinator,
                times(
                        2
                )
        ).tryRunOnce();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_FAILURES
                )
        ).isEqualTo(
                1.0
        );
    }

    /*
     * ============================================================
     * TEST 6
     * LOCAL OVERLAP
     * ============================================================
     */

    @Test
    void shouldRecordLocalOverlapSkip()
            throws Exception {

        CountDownLatch firstExecutionStarted =
                new CountDownLatch(
                        1
                );

        CountDownLatch allowFirstExecutionToFinish =
                new CountDownLatch(
                        1
                );

        when(
                executionCoordinator.tryRunOnce()
        ).thenAnswer(
                invocation -> {

                    firstExecutionStarted
                            .countDown();

                    boolean released =
                            allowFirstExecutionToFinish
                                    .await(
                                            5,
                                            TimeUnit.SECONDS
                                    );

                    assertThat(
                            released
                    ).isTrue();

                    return UploadCleanupExecutionResult
                            .executed(
                                    new UploadCleanupRunResult(
                                            1,
                                            1,
                                            0,
                                            0
                                    )
                            );
                }
        );

        Thread firstThread =
                new Thread(
                        scheduler::runScheduledCleanup
                );

        firstThread.start();

        assertThat(
                firstExecutionStarted
                        .await(
                                5,
                                TimeUnit.SECONDS
                        )
        ).isTrue();

        /*
         * Second invocation local guard-a ilişəcək.
         */

        scheduler.runScheduledCleanup();

        verify(
                executionCoordinator,
                times(
                        1
                )
        ).tryRunOnce();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_LOCAL_SKIPS
                )
        ).isEqualTo(
                1.0
        );

        allowFirstExecutionToFinish
                .countDown();

        firstThread.join(
                5000
        );

        assertThat(
                firstThread.isAlive()
        ).isFalse();

        /*
         * Lock açıldıqdan sonra yenidən işləməlidir.
         */

        when(
                executionCoordinator.tryRunOnce()
        ).thenReturn(
                UploadCleanupExecutionResult
                        .executed(
                                new UploadCleanupRunResult(
                                        0,
                                        0,
                                        0,
                                        0
                                )
                        )
        );

        scheduler.runScheduledCleanup();

        verify(
                executionCoordinator,
                times(
                        2
                )
        ).tryRunOnce();

        assertThat(
                counter(
                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS
                )
        ).isEqualTo(
                3.0
        );
    }

    /*
     * ============================================================
     * HELPER
     * ============================================================
     */

    private double counter(
            String name
    ) {

        return meterRegistry
                .get(
                        name
                )
                .counter()
                .count();
    }
}