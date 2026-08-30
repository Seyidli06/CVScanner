package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.upload.domain.CvUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UploadCleanupPolicyTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-08-21T12:00:00Z"
            );

    private UploadCleanupProperties properties;

    private UploadCleanupPolicy policy;

    @BeforeEach
    void setUp() {

        properties =
                new UploadCleanupProperties();

        properties.setEnabled(
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

        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        policy =
                new UploadCleanupPolicy(
                        properties,
                        clock
                );
    }

    






    @Test
    void shouldNotCleanupRecentlyCompletedUpload() {

        CvUpload upload =
                completedUpload();

        









        assertThat(
                upload.getCompletedAt()
        ).isNotNull();

        



        assertThat(
                policy.isEligibleForCleanup(
                        upload
                )
        ).isFalse();
    }

    






    @Test
    void shouldNotCleanupUploadedState() {

        CvUpload upload =
                new CvUpload(
                        "uploaded.zip"
                );

        upload.registerDiscoveredFiles(
                1
        );

        assertThat(
                policy.isEligibleForCleanup(
                        upload
                )
        ).isFalse();
    }

    






    @Test
    void shouldNotCleanupProcessingState() {

        CvUpload upload =
                new CvUpload(
                        "processing.zip"
                );

        upload.registerDiscoveredFiles(
                1
        );

        upload.markProcessing();

        assertThat(
                policy.isEligibleForCleanup(
                        upload
                )
        ).isFalse();
    }

    








    @Test
    void shouldNotCleanupFailedUpload() {

        CvUpload upload =
                new CvUpload(
                        "failed.zip"
                );

        upload.registerDiscoveredFiles(
                1
        );

        upload.markProcessing();

        upload.fail();

        assertThat(
                policy.isEligibleForCleanup(
                        upload
                )
        ).isFalse();
    }

    









    @Test
    void shouldRespectRetentionForCompletedWithErrors() {

        CvUpload upload =
                new CvUpload(
                        "partial.zip"
                );

        upload.registerDiscoveredFiles(
                2
        );

        upload.markProcessing();

        upload.synchronizeProcessingResult(
                1,
                1
        );

        upload.complete();

        assertThat(
                policy.isEligibleForCleanup(
                        upload
                )
        ).isFalse();
    }

    






    @Test
    void shouldNotCleanupAnythingWhenDisabled() {

        properties.setEnabled(
                false
        );

        CvUpload upload =
                completedUpload();

        assertThat(
                policy.isEligibleForCleanup(
                        upload
                )
        ).isFalse();
    }

    





    private CvUpload completedUpload() {

        CvUpload upload =
                new CvUpload(
                        "completed.zip"
                );

        upload.registerDiscoveredFiles(
                1
        );

        upload.markProcessing();

        upload.synchronizeProcessingResult(
                1,
                0
        );

        upload.complete();

        return upload;
    }
}