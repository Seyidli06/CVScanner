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

    /*
     * ============================================================
     * SPRING PRODUCTION CONSTRUCTOR
     * ============================================================
     *
     * Class-da birdən çox constructor olduğu üçün
     * Spring-ə açıq şəkildə deyirik ki:
     *
     * bean yaradarkən BU constructor istifadə olunmalıdır.
     *
     * Clock production-da UTC system clock-dur.
     */

    @Autowired
    public UploadCleanupPolicy(
            UploadCleanupProperties properties
    ) {

        this(
                properties,
                Clock.systemUTC()
        );
    }

    /*
     * ============================================================
     * TEST CONSTRUCTOR
     * ============================================================
     *
     * Package-private saxlanılır.
     *
     * Unit test fixed Clock ötürə bilər:
     *
     * new UploadCleanupPolicy(properties, fixedClock)
     *
     * Amma Spring bunu production constructor kimi
     * istifadə etməyəcək.
     */

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

    /*
     * ============================================================
     * CLEANUP DECISION
     * ============================================================
     */

    public boolean isEligibleForCleanup(
            CvUpload upload
    ) {

        Objects.requireNonNull(
                upload,
                "upload must not be null"
        );

        /*
         * ========================================================
         * GLOBAL KILL SWITCH
         * ========================================================
         */

        if (
                !properties.isEnabled()
        ) {

            return false;
        }

        /*
         * ========================================================
         * STATUS SAFETY GATE
         * ========================================================
         *
         * Cleanup allowed:
         *
         * COMPLETED
         * COMPLETED_WITH_ERRORS
         *
         *
         * Cleanup forbidden:
         *
         * UPLOADED
         * PROCESSING
         * FAILED
         *
         *
         * FAILED xüsusilə saxlanılır:
         *
         * restart
         * debugging
         * failure investigation
         *
         * üçün storage lazım ola bilər.
         */

        if (
                !isCleanupEligibleStatus(
                        upload.getStatus()
                )
        ) {

            return false;
        }

        /*
         * ========================================================
         * COMPLETED TIMESTAMP
         * ========================================================
         *
         * Terminal success-like upload normalda completedAt
         * saxlamalıdır.
         *
         * Amma legacy/corrupt row üçün null olarsa:
         *
         * fail-safe davranırıq
         * və storage silmirik.
         */

        OffsetDateTime completedAt =
                upload.getCompletedAt();

        if (
                completedAt == null
        ) {

            return false;
        }

        /*
         * ========================================================
         * RETENTION CUTOFF
         * ========================================================
         *
         * Example:
         *
         * now       = 21 Aug
         * retention = 7 days
         *
         * cutoff    = 14 Aug
         *
         *
         * completedAt <= cutoff
         *
         * => cleanup eligible
         */

        OffsetDateTime cutoff =
                OffsetDateTime
                        .now(
                                clock
                        )
                        .minus(
                                properties
                                        .getCompletedRetention()
                        );

        /*
         * Exactly cutoff zamanı tamamlanmış upload
         * retention intervalını artıq tamamlayıb.
         */

        return !completedAt.isAfter(
                cutoff
        );
    }

    /*
     * ============================================================
     * STATUS POLICY
     * ============================================================
     */

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