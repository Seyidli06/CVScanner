package com.adil.cvscanner.upload.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class UploadCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    UploadCleanupScheduler.class
            );

    private final UploadCleanupProperties properties;

    private final UploadCleanupExecutionCoordinator
            executionCoordinator;

    private final UploadCleanupMetrics cleanupMetrics;

    private final AtomicBoolean running =
            new AtomicBoolean(
                    false
            );

    public UploadCleanupScheduler(
            UploadCleanupProperties properties,
            UploadCleanupExecutionCoordinator executionCoordinator,
            UploadCleanupMetrics cleanupMetrics
    ) {

        this.properties =
                properties;

        this.executionCoordinator =
                executionCoordinator;

        this.cleanupMetrics =
                cleanupMetrics;
    }

    @Scheduled(
            initialDelayString =
                    "${app.cleanup.initial-delay:PT1M}",

            fixedDelayString =
                    "${app.cleanup.schedule-delay:PT1H}"
    )
    public void runScheduledCleanup() {

        if (
                !properties.isSchedulerEnabled()
        ) {

            return;
        }

        if (
                !properties.isEnabled()
        ) {

            return;
        }

        cleanupMetrics
                .recordSchedulerAttempt();

        if (
                !running.compareAndSet(
                        false,
                        true
                )
        ) {

            cleanupMetrics
                    .recordSchedulerLocalSkip();

            LOGGER.info(
                    "UPLOAD_STORAGE_CLEANUP_SKIPPED "
                            +
                            "reason=local-run-already-active"
            );

            return;
        }

        try {

            UploadCleanupExecutionResult executionResult =
                    executionCoordinator
                            .tryRunOnce();

            if (
                    !executionResult.executed()
            ) {

                LOGGER.info(
                        "UPLOAD_STORAGE_CLEANUP_SKIPPED "
                                +
                                "reason=distributed-lock-held"
                );

                return;
            }

            UploadCleanupRunResult cleanupResult =
                    executionResult
                            .cleanupResult();

            LOGGER.info(
                    "UPLOAD_STORAGE_CLEANUP_RUN_COMPLETED "
                            +
                            "selected={} "
                            +
                            "deleted={} "
                            +
                            "alreadyAbsent={} "
                            +
                            "failed={}",
                    cleanupResult.selected(),
                    cleanupResult.deleted(),
                    cleanupResult.alreadyAbsent(),
                    cleanupResult.failed()
            );

        } catch (
                RuntimeException exception
        ) {

            cleanupMetrics
                    .recordSchedulerFailure();

            LOGGER.warn(
                    "UPLOAD_STORAGE_CLEANUP_RUN_FAILED "
                            +
                            "errorType={}",
                    exception
                            .getClass()
                            .getSimpleName()
            );

        } finally {

            running.set(
                    false
            );
        }
    }
}
