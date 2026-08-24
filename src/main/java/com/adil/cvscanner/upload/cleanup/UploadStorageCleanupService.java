package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class UploadStorageCleanupService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    UploadStorageCleanupService.class
            );

    private final UploadCleanupCandidateFinder
            cleanupCandidateFinder;

    private final LocalUploadStorage
            uploadStorage;

    private final UploadStorageCleanupRecordRepository
            cleanupRecordRepository;

    private final UploadCleanupMetrics
            cleanupMetrics;

    public UploadStorageCleanupService(
            UploadCleanupCandidateFinder cleanupCandidateFinder,
            LocalUploadStorage uploadStorage,
            UploadStorageCleanupRecordRepository cleanupRecordRepository,
            UploadCleanupMetrics cleanupMetrics
    ) {

        this.cleanupCandidateFinder =
                cleanupCandidateFinder;

        this.uploadStorage =
                uploadStorage;

        this.cleanupRecordRepository =
                cleanupRecordRepository;

        this.cleanupMetrics =
                cleanupMetrics;
    }

    /*
     * ============================================================
     * ONE CLEANUP EXECUTION
     * ============================================================
     */

    public UploadCleanupRunResult runOnce() {

        /*
         * System.currentTimeMillis() duration ölçmək üçün
         * ideal deyil.
         *
         * Wall clock dəyişə bilər.
         *
         * Duration üçün monotonic System.nanoTime()
         * istifadə edirik.
         */

        long startedAtNanos =
                System.nanoTime();

        try {

            List<CvUpload> candidates =
                    cleanupCandidateFinder
                            .findNextBatch();

            int deleted =
                    0;

            int alreadyAbsent =
                    0;

            int failed =
                    0;

            for (
                    CvUpload upload : candidates
            ) {

                UUID uploadId =
                        upload.getId();

                try {

                    /*
                     * =============================================
                     * FILESYSTEM
                     * =============================================
                     */

                    boolean storageExisted =
                            uploadStorage
                                    .deleteUploadDirectory(
                                            uploadId
                                    );

                    /*
                     * =============================================
                     * CLEANUP AUDIT MARKER
                     * =============================================
                     */

                    UploadStorageCleanupRecord record =
                            new UploadStorageCleanupRecord(
                                    uploadId,
                                    OffsetDateTime.now(
                                            ZoneOffset.UTC
                                    )
                            );

                    cleanupRecordRepository
                            .saveAndFlush(
                                    record
                            );

                    if (
                            storageExisted
                    ) {

                        deleted++;

                    } else {

                        alreadyAbsent++;
                    }

                    LOGGER.info(
                            "UPLOAD_STORAGE_CLEANED "
                                    +
                                    "uploadId={} "
                                    +
                                    "storageExisted={}",
                            uploadId,
                            storageExisted
                    );

                } catch (
                        RuntimeException exception
                ) {

                    failed++;

                    /*
                     * Full filesystem path,
                     * filename,
                     * CV text,
                     * exception message
                     *
                     * log edilmir.
                     */

                    LOGGER.warn(
                            "UPLOAD_STORAGE_CLEANUP_FAILED "
                                    +
                                    "uploadId={} "
                                    +
                                    "errorType={}",
                            uploadId,
                            exception
                                    .getClass()
                                    .getSimpleName()
                    );
                }
            }

            UploadCleanupRunResult result =
                    new UploadCleanupRunResult(
                            candidates.size(),
                            deleted,
                            alreadyAbsent,
                            failed
                    );

            /*
             * =============================================
             * METRICS
             * =============================================
             */

            cleanupMetrics
                    .recordCompletedRun(
                            result,
                            elapsedSince(
                                    startedAtNanos
                            )
                    );

            return result;

        } catch (
                RuntimeException exception
        ) {

            /*
             * Whole run result yarada bilmədi.
             */

            cleanupMetrics
                    .recordRunFailure(
                            elapsedSince(
                                    startedAtNanos
                            )
                    );

            throw exception;
        }
    }

    /*
     * ============================================================
     * MONOTONIC EXECUTION DURATION
     * ============================================================
     */

    private Duration elapsedSince(
            long startedAtNanos
    ) {

        long elapsedNanos =
                System.nanoTime()
                        - startedAtNanos;

        /*
         * System.nanoTime monotonic olduğuna görə normalda
         * negative mümkün deyil.
         *
         * Defensive clamp saxlayırıq.
         */

        if (
                elapsedNanos < 0
        ) {

            elapsedNanos =
                    0;
        }

        return Duration.ofNanos(
                elapsedNanos
        );
    }
}