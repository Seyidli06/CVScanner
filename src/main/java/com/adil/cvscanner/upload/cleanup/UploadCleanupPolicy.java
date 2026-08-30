package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

@Component
public class UploadCleanupPolicy {

    private final UploadCleanupProperties properties;

    private final Clock clock;

    @Autowired
    public UploadCleanupPolicy(
            UploadCleanupProperties properties
    ) {

        this(
                properties,
                Clock.systemUTC()
        );
    }

    UploadCleanupPolicy(
            UploadCleanupProperties properties,
            Clock clock
    ) {

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    public boolean isEligibleForCleanup(
            CvUpload upload
    ) {

        Objects.requireNonNull(
                upload,
                "upload must not be null"
        );

        if (
                !properties.isEnabled()
        ) {

            return false;
        }

        if (
                !isCleanupEligibleStatus(
                        upload.getStatus()
                )
        ) {

            return false;
        }

        OffsetDateTime completedAt =
                upload.getCompletedAt();

        if (
                completedAt == null
        ) {

            return false;
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

        return !completedAt.isAfter(
                cutoff
        );
    }

    private boolean isCleanupEligibleStatus(
            UploadStatus status
    ) {

        return status
                == UploadStatus.COMPLETED
                ||
                status
                        == UploadStatus.COMPLETED_WITH_ERRORS;
    }
}
