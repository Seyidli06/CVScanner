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



    private static final Path STORAGE_ROOT =
            createStorageRoot();

    





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

    





    @BeforeEach
    void setUp() {

        



        candidateRepository.deleteAll();

        cleanupRecordRepository.deleteAll();

        cvUploadRepository.deleteAll();
    }

    













    @Test
    void shouldDeleteOnlyEligibleStorageAndKeepDatabaseData()
            throws Exception {

        





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

        





        UploadCleanupRunResult result =
                cleanupService.runOnce();

        



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

        





        assertThat(
                Files.notExists(
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

        





        assertThat(
                candidateRepository.findAllByUpload_Id(
                        oldCompleted.getId()
                )
        ).hasSize(
                1
        );

        





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



        UploadCleanupRunResult secondRun =
                cleanupService.runOnce();

        assertThat(
                secondRun.selected()
        ).isZero();
    }

    







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



            assertThat(
                    Files.exists(
                            outsideFile
                    )
            ).isTrue();

        } finally {


            Files.deleteIfExists(
                    outsideFile
            );

            Files.deleteIfExists(
                    outsideDirectory
            );
        }
    }

    





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

        



        Files.writeString(
                cvsDirectory.resolve(
                        "resume.pdf"
                ),
                "integration-test-cv"
        );



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