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


    private static final Path STORAGE_ROOT =
            createStorageRoot();


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

    







    @Test
    void shouldSelectOnlyOldCompletedUploads() {

        







        CvUpload oldCompleted =
                completedUpload(
                        "old-completed.zip"
                );

        moveCompletedAtBack(
                oldCompleted.getId(),
                10
        );

        







        CvUpload oldPartial =
                completedWithErrorsUpload(
                        "old-partial.zip"
                );

        moveCompletedAtBack(
                oldPartial.getId(),
                9
        );


        CvUpload recentCompleted =
                completedUpload(
                        "recent-completed.zip"
                );

        moveCompletedAtBack(
                recentCompleted.getId(),
                2
        );


        CvUpload failed =
                failedUpload(
                        "failed.zip"
                );

        moveCompletedAtBack(
                failed.getId(),
                30
        );


        CvUpload processing =
                processingUpload(
                        "processing.zip"
                );

        moveCompletedAtBack(
                processing.getId(),
                30
        );


        CvUpload uploaded =
                uploadedUpload(
                        "uploaded.zip"
                );

        moveCompletedAtBack(
                uploaded.getId(),
                30
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
                oldCompleted.getId(),
                oldPartial.getId()
        );

        


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