package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import com.adil.cvscanner.upload.infrastructure.UploadStorageException;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class UploadStorageCleanupServiceIT {

    /*
     * ============================================================
     * STORAGE ROOT
     * ============================================================
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
                            "cvscanner_storage_cleanup_test"
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
    private CandidateRepository
            candidateRepository;

    @Autowired
    private UploadStorageCleanupRecordRepository
            cleanupRecordRepository;

    @Autowired
    private UploadStorageCleanupService
            cleanupService;

    @Autowired
    private LocalUploadStorage
            uploadStorage;

    @Autowired
    private JdbcTemplate
            jdbcTemplate;

    /*
     * ============================================================
     * TEST CONFIG
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
                () -> "10"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    /*
     * ============================================================
     * CLEAN DB
     * ============================================================
     */

    @BeforeEach
    void setUp() {

        /*
         * FK order.
         */

        candidateRepository.deleteAll();

        cleanupRecordRepository.deleteAll();

        cvUploadRepository.deleteAll();
    }

    /*
     * ============================================================
     * TEST 1
     *
     * DELETE ONLY ELIGIBLE STORAGE
     * +
     * KEEP BUSINESS DATA
     * +
     * AUDIT MARKER
     * +
     * SECOND RUN IDEMPOTENT
     * ============================================================
     */

    @Test
    void shouldDeleteOnlyEligibleStorageAndKeepDatabaseData()
            throws Exception {

        /*
         * ========================================================
         * OLD COMPLETED
         * ========================================================
         */

        CvUpload oldCompleted =
                completedUpload(
                        "old-completed.zip"
                );

        moveCompletedAtBack(
                oldCompleted.getId(),
                10
        );

        createPhysicalStorage(
                oldCompleted.getId()
        );

        /*
         * Parsed Candidate DB-də qalmalıdır.
         */

        Candidate candidate =
                new Candidate(
                        oldCompleted,
                        "Persisted Candidate",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "candidate.pdf",
                        Set.of(
                                "Java",
                                "Spring Boot"
                        )
                );

        candidateRepository.saveAndFlush(
                candidate
        );

        /*
         * ========================================================
         * RECENT COMPLETED
         * ========================================================
         *
         * Retention daxilindədir.
         */

        CvUpload recentCompleted =
                completedUpload(
                        "recent-completed.zip"
                );

        moveCompletedAtBack(
                recentCompleted.getId(),
                2
        );

        createPhysicalStorage(
                recentCompleted.getId()
        );

        /*
         * ========================================================
         * FAILED
         * ========================================================
         *
         * Hətta 30 gün köhnə olsa belə qorunmalıdır.
         */

        CvUpload failed =
                failedUpload(
                        "failed.zip"
                );

        moveCompletedAtBack(
                failed.getId(),
                30
        );

        createPhysicalStorage(
                failed.getId()
        );

        /*
         * ========================================================
         * PRE-CONDITIONS
         * ========================================================
         */

        assertThat(
                Files.exists(
                        uploadStorage.uploadDirectory(
                                oldCompleted.getId()
                        )
                )
        ).isTrue();

        assertThat(
                Files.exists(
                        uploadStorage.uploadDirectory(
                                recentCompleted.getId()
                        )
                )
        ).isTrue();

        assertThat(
                Files.exists(
                        uploadStorage.uploadDirectory(
                                failed.getId()
                        )
                )
        ).isTrue();

        /*
         * ========================================================
         * ACT
         * ========================================================
         */

        UploadCleanupRunResult result =
                cleanupService.runOnce();

        /*
         * Only oldCompleted eligible.
         */

        assertThat(
                result.selected()
        ).isEqualTo(
                1
        );

        assertThat(
                result.deleted()
        ).isEqualTo(
                1
        );

        assertThat(
                result.alreadyAbsent()
        ).isZero();

        assertThat(
                result.failed()
        ).isZero();

        assertThat(
                result.completed()
        ).isEqualTo(
                1
        );

        /*
         * ========================================================
         * FILESYSTEM
         * ========================================================
         */

        assertThat(
                Files.notExists(
                        uploadStorage.uploadDirectory(
                                oldCompleted.getId()
                        )
                )
        ).isTrue();

        /*
         * Recent completed untouched.
         */

        assertThat(
                Files.exists(
                        uploadStorage.uploadDirectory(
                                recentCompleted.getId()
                        )
                )
        ).isTrue();

        /*
         * Failed untouched.
         */

        assertThat(
                Files.exists(
                        uploadStorage.uploadDirectory(
                                failed.getId()
                        )
                )
        ).isTrue();

        /*
         * ========================================================
         * DB UPLOAD ROWS MUST REMAIN
         * ========================================================
         */

        assertThat(
                cvUploadRepository.existsById(
                        oldCompleted.getId()
                )
        ).isTrue();

        assertThat(
                cvUploadRepository.existsById(
                        recentCompleted.getId()
                )
        ).isTrue();

        assertThat(
                cvUploadRepository.existsById(
                        failed.getId()
                )
        ).isTrue();

        /*
         * ========================================================
         * CANDIDATE BUSINESS DATA MUST REMAIN
         * ========================================================
         */

        assertThat(
                candidateRepository.findAllByUpload_Id(
                        oldCompleted.getId()
                )
        ).hasSize(
                1
        );

        /*
         * ========================================================
         * CLEANUP AUDIT
         * ========================================================
         */

        assertThat(
                cleanupRecordRepository.existsById(
                        oldCompleted.getId()
                )
        ).isTrue();

        assertThat(
                cleanupRecordRepository.existsById(
                        recentCompleted.getId()
                )
        ).isFalse();

        assertThat(
                cleanupRecordRepository.existsById(
                        failed.getId()
                )
        ).isFalse();

        /*
         * ========================================================
         * SECOND RUN
         * ========================================================
         *
         * oldCompleted artıq marker daşıyır.
         *
         * Finder onu bir daha seçməməlidir.
         */

        UploadCleanupRunResult secondRun =
                cleanupService.runOnce();

        assertThat(
                secondRun.selected()
        ).isZero();

        assertThat(
                secondRun.deleted()
        ).isZero();

        assertThat(
                secondRun.alreadyAbsent()
        ).isZero();

        assertThat(
                secondRun.failed()
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 2
     *
     * STORAGE ALREADY ABSENT
     * ============================================================
     *
     * DB cleanup marker yoxdur,
     * amma physical directory artıq yoxdur.
     *
     * Bu error olmamalıdır.
     *
     * Audit marker yaradılmalıdır ki upload
     * sonsuza qədər finder-ə düşməsin.
     */

    @Test
    void shouldMarkCleanupWhenStorageIsAlreadyAbsent() {

        CvUpload upload =
                completedUpload(
                        "already-missing.zip"
                );

        moveCompletedAtBack(
                upload.getId(),
                10
        );

        /*
         * Qəsdən physical storage yaratmırıq.
         */

        assertThat(
                Files.notExists(
                        uploadStorage.uploadDirectory(
                                upload.getId()
                        )
                )
        ).isTrue();

        UploadCleanupRunResult result =
                cleanupService.runOnce();

        assertThat(
                result.selected()
        ).isEqualTo(
                1
        );

        assertThat(
                result.deleted()
        ).isZero();

        assertThat(
                result.alreadyAbsent()
        ).isEqualTo(
                1
        );

        assertThat(
                result.failed()
        ).isZero();

        assertThat(
                cleanupRecordRepository.existsById(
                        upload.getId()
                )
        ).isTrue();

        /*
         * Next run candidate deyil.
         */

        UploadCleanupRunResult secondRun =
                cleanupService.runOnce();

        assertThat(
                secondRun.selected()
        ).isZero();
    }

    /*
     * ============================================================
     * TEST 3
     *
     * STORAGE ROOT ESCAPE PROTECTION
     * ============================================================
     */

    @Test
    void shouldRefuseRecursiveDeletionOutsideStorageRoot()
            throws Exception {

        Path outsideDirectory =
                Files.createTempDirectory(
                        "cvscanner-outside-storage-"
                );

        Path outsideFile =
                outsideDirectory.resolve(
                        "must-survive.txt"
                );

        Files.writeString(
                outsideFile,
                "do not delete"
        );

        try {

            assertThatThrownBy(
                    () ->
                            uploadStorage
                                    .deleteRecursively(
                                            outsideDirectory
                                    )
            )
                    .isInstanceOf(
                            UploadStorageException.class
                    );

            /*
             * Security guard işlədiyi üçün
             * external file hələ mövcuddur.
             */

            assertThat(
                    Files.exists(
                            outsideFile
                    )
            ).isTrue();

        } finally {

            /*
             * Test öz external temp faylını özü
             * təmizləyir.
             */

            Files.deleteIfExists(
                    outsideFile
            );

            Files.deleteIfExists(
                    outsideDirectory
            );
        }
    }

    /*
     * ============================================================
     * CREATE COMPLETED UPLOAD
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
     * CREATE FAILED UPLOAD
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
     * REAL PHYSICAL STORAGE
     * ============================================================
     */

    private void createPhysicalStorage(
            UUID uploadId
    ) throws IOException {

        Path cvsDirectory =
                uploadStorage
                        .extractionDirectory(
                                uploadId
                        )
                        .resolve(
                                "nested"
                        );

        Files.createDirectories(
                cvsDirectory
        );

        /*
         * Extracted CV.
         */

        Files.writeString(
                cvsDirectory.resolve(
                        "resume.pdf"
                ),
                "integration-test-cv"
        );

        /*
         * Upload directory daxilində əlavə artifact.
         *
         * Whole upload directory cleanup edilməlidir,
         * yalnız cvs/ yox.
         */

        Files.writeString(
                uploadStorage
                        .uploadDirectory(
                                uploadId
                        )
                        .resolve(
                                "manifest.txt"
                        ),
                "integration-test-manifest"
        );
    }

    /*
     * ============================================================
     * MOVE COMPLETED_AT BACK
     * ============================================================
     *
     * Production entity-yə test-only setter əlavə etmirik.
     */

    private void moveCompletedAtBack(
            UUID uploadId,
            int days
    ) {

        OffsetDateTime timestamp =
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
                                            timestamp
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


    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-storage-cleanup-it-"
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