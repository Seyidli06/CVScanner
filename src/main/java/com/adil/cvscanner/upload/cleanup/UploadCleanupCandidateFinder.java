package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class UploadCleanupCandidateFinder {

    private static final Set<UploadStatus> CLEANUP_STATUSES =
            EnumSet.of(
                    UploadStatus.COMPLETED,
                    UploadStatus.COMPLETED_WITH_ERRORS
            );

    private final UploadCleanupCandidateRepository
            cleanupCandidateRepository;

    private final UploadCleanupProperties
            properties;

    private final UploadCleanupPolicy
            cleanupPolicy;

    private final Clock clock;

    public UploadCleanupCandidateFinder(
            UploadCleanupCandidateRepository cleanupCandidateRepository,
            UploadCleanupProperties properties,
            UploadCleanupPolicy cleanupPolicy
    ) {

        this.cleanupCandidateRepository =
                cleanupCandidateRepository;

        this.properties =
                properties;

        this.cleanupPolicy =
                cleanupPolicy;

        this.clock =
                Clock.systemUTC();
    }

    @Transactional(
            readOnly = true
    )
    public List<CvUpload> findNextBatch() {

        if (
                !properties.isEnabled()
        ) {

            return List.of();
        }

        OffsetDateTime cutoff =
                OffsetDateTime
                        .now(
                                clock
                        )
                        .minus(
                                properties
                                        .getCompletedRetention()
                        );

        List<CvUpload> databaseCandidates =
                cleanupCandidateRepository
                        .findCleanupCandidates(
                                CLEANUP_STATUSES,
                                cutoff,
                                PageRequest.of(
                                        0,
                                        properties
                                                .getBatchSize()
                                )
                        );

        return databaseCandidates
                .stream()
                .filter(
                        cleanupPolicy::isEligibleForCleanup
                )
                .toList();
    }
}
