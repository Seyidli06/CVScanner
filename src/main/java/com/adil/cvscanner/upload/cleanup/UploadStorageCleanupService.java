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

    public UploadCleanupRunResult runOnce() {

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

                    boolean storageExisted =
                            uploadStorage
                                    .deleteUploadDirectory(
                                            uploadId
                                    );

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

            cleanupMetrics
                    .recordRunFailure(
                            elapsedSince(
                                    startedAtNanos
                            )
                    );

            throw exception;
        }
    }

    private Duration elapsedSince(
            long startedAtNanos
    ) {

        long elapsedNanos =
                System.nanoTime()
                        - startedAtNanos;

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
