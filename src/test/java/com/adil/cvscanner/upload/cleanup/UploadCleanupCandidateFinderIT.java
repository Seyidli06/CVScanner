package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UploadCleanupCandidateFinderIT {

    /*
     * ============================================================
     * TEMP STORAGE
     * ============================================================
     *
     * Application context real LocalUploadStorage
     * yaratdığı üçün test üçün isolated root.
     */

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     */

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_cleanup_candidate_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private CvUploadRepository
            cvUploadRepository;

    @Autowired
    private UploadCleanupCandidateFinder
            cleanupCandidateFinder;

    @Autowired
    private JdbcTemplate
            jdbcTemplate;

    /*
     * ============================================================
     * CONFIG
     * ============================================================
     */

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "app.upload.storage-root",
                () -> STORAGE_ROOT.toString()
        );

        /*
         * Cleanup:
         *
         * enabled
         * 7 day retention
         * max 2 rows per execution
         */
        registry.add(
                "app.cleanup.enabled",
                () -> "true"
        );

        registry.add(
                "app.cleanup.completed-retention",
                () -> "7d"
        );

        registry.add(
                "app.cleanup.batch-size",
                () -> "2"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    @BeforeEach
    void setUp() {

        cvUploadRepository.deleteAll();
    }

    /*
     * ============================================================
     * TEST 1
     *
     * ONLY OLD SUCCESS-LIKE TERMINAL UPLOADS
     * ============================================================
     */

    @Test
    void shouldSelectOnlyOldCompletedUploads() {

        /*
         * ========================================================
         * OLD COMPLETED
         * ========================================================
         *
         * Eligible.
         */

        CvUpload oldCompleted =
                completedUpload(
                        "old-completed.zip"
                );

        moveCompletedAtBack(
                oldCompleted.getId(),
                10
        );

        /*
         * ========================================================
         * OLD COMPLETED WITH ERRORS
         * ========================================================
         *
         * Eligible.
         */

        CvUpload oldPartial =
                completedWithErrorsUpload(
                        "old-partial.zip"
                );

        moveCompletedAtBack(
                oldPartial.getId(),
                9
        );

        /*
         * ========================================================
         * RECENT COMPLETED
         * ========================================================
         *
         * Terminal olsa da retention keçməyib.
         */

        CvUpload recentCompleted =
                completedUpload(
                        "recent-completed.zip"
                );

        moveCompletedAtBack(
                recentCompleted.getId(),
                2
        );

        /*
         * ========================================================
         * FAILED
         * ========================================================
         *
         * completedAt köhnə olsa belə cleanup olmamalıdır.
         */

        CvUpload failed =
                failedUpload(
                        "failed.zip"
                );

        moveCompletedAtBack(
                failed.getId(),
                30
        );

        /*
         * ========================================================
         * PROCESSING
         * ========================================================
         *
         * Corrupted/legacy row simulyasiya etmək üçün
         * completed_at-a hətta köhnə tarix yazırıq.
         *
         * Status PROCESSING olduğu üçün yenə cleanup
         * candidate olmamalıdır.
         */

        CvUpload processing =
                processingUpload(
                        "processing.zip"
                );

        moveCompletedAtBack(
                processing.getId(),
                30
        );

        /*
         * ========================================================
         * UPLOADED
         * ========================================================
         *
         * Eyni defence:
         *
         * completed_at səhvən dolu olsa belə
         * status cleanup-a uyğun deyil.
         */

        CvUpload uploaded =
                uploadedUpload(
                        "uploaded.zip"
                );

        moveCompletedAtBack(
                uploaded.getId(),
                30
        );

        /*
         * ========================================================
         * ACT
         * ========================================================
         */

        List<CvUpload> candidates =
                cleanupCandidateFinder
                        .findNextBatch();

        /*
         * batch-size = 2
         *
         * və eligible dəqiq 2 upload var.
         */
        assertThat(
                candidates
        ).hasSize(
                2
        );

        assertThat(
                candidates
                        .stream()
                        .map(
                                CvUpload::getId
                        )
                        .toList()
        ).containsExactly(
                oldCompleted.getId(),
                oldPartial.getId()
        );

        /*
         * Safety asserts.
         */
        assertThat(
                candidates
                        .stream()
                        .map(
                                CvUpload::getStatus
                        )
                        .toList()
        ).containsExactly(
                UploadStatus.COMPLETED,
                UploadStatus.COMPLETED_WITH_ERRORS
        );

        assertThat(
                candidates
                        .stream()
                        .map(
                                CvUpload::getId
                        )
                        .toList()
        )
                .doesNotContain(
                        recentCompleted.getId()
                )
                .doesNotContain(
                        failed.getId()
                )
                .doesNotContain(
                        processing.getId()
                )
                .doesNotContain(
                        uploaded.getId()
                );
    }

    /*
     * ============================================================
     * TEST 2
     *
     * BOUNDED BATCH + OLDEST FIRST
     * ============================================================
     *
     * 3 eligible upload var.
     *
     * batch-size = 2.
     *
     * Expected:
     *
     * yalnız ən köhnə 2.
     */

    @Test
    void shouldRespectBatchSizeAndSelectOldestFirst() {

        CvUpload oldest =
                completedUpload(
                        "oldest.zip"
                );

        moveCompletedAtBack(
                oldest.getId(),
                30
        );

        CvUpload middle =
                completedUpload(
                        "middle.zip"
                );

        moveCompletedAtBack(
                middle.getId(),
                20
        );

        CvUpload newest =
                completedUpload(
                        "newest.zip"
                );

        moveCompletedAtBack(
                newest.getId(),
                10
        );

        List<CvUpload> candidates =
                cleanupCandidateFinder
                        .findNextBatch();

        assertThat(
                candidates
        ).hasSize(
                2
        );

        assertThat(
                candidates
                        .stream()
                        .map(
                                CvUpload::getId
                        )
                        .toList()
        ).containsExactly(
                oldest.getId(),
                middle.getId()
        );

        assertThat(
                candidates
                        .stream()
                        .map(
                                CvUpload::getId
                        )
                        .toList()
        ).doesNotContain(
                newest.getId()
        );
    }

    /*
     * ============================================================
     * COMPLETED FACTORY
     * ============================================================
     */

    private CvUpload completedUpload(
            String filename
    ) {

        CvUpload upload =
                new CvUpload(
                        filename
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

        return cvUploadRepository
                .saveAndFlush(
                        upload
                );
    }

    /*
     * ============================================================
     * COMPLETED WITH ERRORS FACTORY
     * ============================================================
     */

    private CvUpload completedWithErrorsUpload(
            String filename
    ) {

        CvUpload upload =
                new CvUpload(
                        filename
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

        return cvUploadRepository
                .saveAndFlush(
                        upload
                );
    }

    /*
     * ============================================================
     * FAILED FACTORY
     * ============================================================
     */

    private CvUpload failedUpload(
            String filename
    ) {

        CvUpload upload =
                new CvUpload(
                        filename
                );

        upload.registerDiscoveredFiles(
                1
        );

        upload.markProcessing();

        upload.fail();

        return cvUploadRepository
                .saveAndFlush(
                        upload
                );
    }

    /*
     * ============================================================
     * PROCESSING FACTORY
     * ============================================================
     */

    private CvUpload processingUpload(
            String filename
    ) {

        CvUpload upload =
                new CvUpload(
                        filename
                );

        upload.registerDiscoveredFiles(
                1
        );

        upload.markProcessing();

        return cvUploadRepository
                .saveAndFlush(
                        upload
                );
    }

    /*
     * ============================================================
     * UPLOADED FACTORY
     * ============================================================
     */

    private CvUpload uploadedUpload(
            String filename
    ) {

        CvUpload upload =
                new CvUpload(
                        filename
                );

        upload.registerDiscoveredFiles(
                1
        );

        return cvUploadRepository
                .saveAndFlush(
                        upload
                );
    }

    /*
     * ============================================================
     * TEST-ONLY TIMESTAMP CONTROL
     * ============================================================
     *
     * Production entity-yə:
     *
     * setCompletedAt(...)
     *
     * əlavə etmirik.
     *
     * Test real PostgreSQL row-un timestamp-ını
     * birbaşa dəyişir.
     *
     * Bu bizə həqiqi:
     *
     * completed_at <= cutoff
     *
     * query davranışını test etməyə imkan verir.
     */

    private void moveCompletedAtBack(
            UUID uploadId,
            int days
    ) {

        OffsetDateTime completedAt =
                OffsetDateTime
                        .now(
                                ZoneOffset.UTC
                        )
                        .minusDays(
                                days
                        );

        int updated =
                jdbcTemplate.update(
                        """
                        update cv_upload
                        set completed_at = ?
                        where id = ?
                        """,
                        preparedStatement -> {

                            preparedStatement
                                    .setObject(
                                            1,
                                            completedAt
                                    );

                            preparedStatement
                                    .setObject(
                                            2,
                                            uploadId
                                    );
                        }
                );

        assertThat(
                updated
        ).isEqualTo(
                1
        );
    }

    /*
     * ============================================================
     * TEMP STORAGE
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-cleanup-candidate-it-"
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