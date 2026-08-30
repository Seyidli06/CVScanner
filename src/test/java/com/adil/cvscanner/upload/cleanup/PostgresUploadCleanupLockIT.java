package com.adil.cvscanner.upload.cleanup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PostgresUploadCleanupLockIT {

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_cleanup_lock_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "app.upload.storage-root",
                () -> STORAGE_ROOT.toString()
        );

        registry.add(
                "app.cleanup.scheduler-enabled",
                () -> "false"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    @Test
    void shouldRecordDistributedLockAcquisitionAndContention()
            throws Exception {

        SimpleMeterRegistry meterRegistry =
                new SimpleMeterRegistry();

        UploadCleanupMetrics metrics =
                new UploadCleanupMetrics(
                        meterRegistry
                );

        PostgresUploadCleanupLock firstInstance =
                new PostgresUploadCleanupLock(
                        dataSource,
                        metrics
                );

        PostgresUploadCleanupLock secondInstance =
                new PostgresUploadCleanupLock(
                        dataSource,
                        metrics
                );

        CountDownLatch firstLockAcquired =
                new CountDownLatch(
                        1
                );

        CountDownLatch releaseFirstLock =
                new CountDownLatch(
                        1
                );

        AtomicReference<Throwable> firstThreadFailure =
                new AtomicReference<>();

        Thread firstThread =
                new Thread(
                        () -> {

                            try {

                                Optional<String> result =
                                        firstInstance
                                                .tryExecute(
                                                        () -> {

                                                            firstLockAcquired
                                                                    .countDown();

                                                            try {

                                                                boolean released =
                                                                        releaseFirstLock
                                                                                .await(
                                                                                        10,
                                                                                        TimeUnit.SECONDS
                                                                                );

                                                                if (
                                                                        !released
                                                                ) {

                                                                    throw new IllegalStateException(
                                                                            "Timed out waiting to release first lock"
                                                                    );
                                                                }

                                                            } catch (
                                                                    InterruptedException exception
                                                            ) {

                                                                Thread.currentThread()
                                                                        .interrupt();

                                                                throw new IllegalStateException(
                                                                        "Interrupted while holding cleanup lock",
                                                                        exception
                                                                );
                                                            }

                                                            return "first";
                                                        }
                                                );

                                assertThat(
                                        result
                                ).contains(
                                        "first"
                                );

                            } catch (
                                    Throwable throwable
                            ) {

                                firstThreadFailure
                                        .set(
                                                throwable
                                        );
                            }
                        }
                );

        firstThread.start();

        assertThat(
                firstLockAcquired
                        .await(
                                10,
                                TimeUnit.SECONDS
                        )
        ).isTrue();

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_ACQUIRED
                )
        ).isEqualTo(
                1.0
        );

        Optional<String> secondWhileLocked =
                secondInstance
                        .tryExecute(
                                () -> "second"
                        );

        assertThat(
                secondWhileLocked
        ).isEmpty();

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_CONTENDED
                )
        ).isEqualTo(
                1.0
        );

        releaseFirstLock
                .countDown();

        firstThread.join(
                10000
        );

        assertThat(
                firstThread.isAlive()
        ).isFalse();

        assertThat(
                firstThreadFailure.get()
        ).isNull();

        Optional<String> secondAfterRelease =
                secondInstance
                        .tryExecute(
                                () -> "second"
                        );

        assertThat(
                secondAfterRelease
        ).contains(
                "second"
        );

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_ACQUIRED
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_CONTENDED
                )
        ).isEqualTo(
                1.0
        );
    }

    @Test
    void shouldReleaseLockAfterActionFailureAndRecordAcquisition() {

        SimpleMeterRegistry meterRegistry =
                new SimpleMeterRegistry();

        UploadCleanupMetrics metrics =
                new UploadCleanupMetrics(
                        meterRegistry
                );

        PostgresUploadCleanupLock firstInstance =
                new PostgresUploadCleanupLock(
                        dataSource,
                        metrics
                );

        PostgresUploadCleanupLock secondInstance =
                new PostgresUploadCleanupLock(
                        dataSource,
                        metrics
                );

        assertThatThrownBy(
                () ->
                        firstInstance
                                .tryExecute(
                                        () -> {

                                            throw new IllegalStateException(
                                                    "simulated cleanup failure"
                                            );
                                        }
                                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "simulated cleanup failure"
                );

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_ACQUIRED
                )
        ).isEqualTo(
                1.0
        );

        Optional<String> recovered =
                secondInstance
                        .tryExecute(
                                () -> "recovered"
                        );

        assertThat(
                recovered
        ).contains(
                "recovered"
        );

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_ACQUIRED
                )
        ).isEqualTo(
                2.0
        );

        assertThat(
                counter(
                        meterRegistry,
                        UploadCleanupMetrics.LOCK_CONTENDED
                )
        ).isZero();
    }

    private double counter(
            SimpleMeterRegistry meterRegistry,
            String metricName
    ) {

        return meterRegistry
                .get(
                        metricName
                )
                .counter()
                .count();
    }

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-cleanup-lock-it-"
            );

        } catch (
                IOException exception
        ) {

            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }
}
