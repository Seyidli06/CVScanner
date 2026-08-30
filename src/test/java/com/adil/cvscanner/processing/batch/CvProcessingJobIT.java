package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.processing.domain.ProcessingFailure;
import com.adil.cvscanner.processing.infrastructure.ProcessingFailureRepository;
import com.adil.cvscanner.testsupport.TestDocumentFactory;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CvProcessingJobIT {

    private static final String STEP_NAME =
            "processCvFilesStep";

    private static final Duration JOB_TIMEOUT =
            Duration.ofSeconds(15);

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job cvProcessingJob;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private LocalUploadStorage uploadStorage;

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private ProcessingFailureRepository processingFailureRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "app.upload.storage-root",
                () -> STORAGE_ROOT.toString()
        );

        registry.add(
                "app.upload.max-entries",
                () -> "100"
        );

        registry.add(
                "app.upload.max-extracted-size",
                () -> "100MB"
        );

        registry.add(
                "app.upload.max-single-file-size",
                () -> "10MB"
        );

        registry.add(
                "app.parsing.max-text-length",
                () -> "1000000"
        );

        registry.add(
                "app.batch.core-pool-size",
                () -> "2"
        );

        registry.add(
                "app.batch.max-pool-size",
                () -> "4"
        );

        registry.add(
                "app.batch.queue-capacity",
                () -> "100"
        );

        registry.add(
                "app.batch.await-termination-seconds",
                () -> "30"
        );
    }

    @BeforeEach
    void cleanBeforeTest()
            throws IOException {

        processingFailureRepository.deleteAll();

        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();

        clearStorage();
    }

    @Test
    void shouldProcessAllCandidatesAndCompleteUpload()
            throws Exception {

        CvUpload upload =
                new CvUpload(
                        "valid-candidates.zip"
                );

        upload.registerDiscoveredFiles(
                2
        );

        cvUploadRepository.saveAndFlush(
                upload
        );

        UUID uploadId =
                upload.getId();

        assertThat(
                upload.getStatus()
        ).isEqualTo(
                UploadStatus.UPLOADED
        );

        assertThat(
                upload.getTotalFiles()
        ).isEqualTo(
                2
        );

        Path extractionDirectory =
                uploadStorage
                        .extractionDirectory(
                                uploadId
                        );

        Path backendDirectory =
                extractionDirectory.resolve(
                        "backend"
                );

        Files.createDirectories(
                backendDirectory
        );

        TestDocumentFactory.createDocx(
                extractionDirectory.resolve(
                        "jane.docx"
                ),
                "Jane Doe",
                "Senior Java Developer",
                "Location: Baku",
                "Preferred work type: Hybrid",
                "7 years experience",
                "Java",
                "Spring Boot",
                "Redis",
                "Docker"
        );

        TestDocumentFactory.createPdf(
                backendDirectory.resolve(
                        "john.pdf"
                ),
                "John Smith",
                "Java Backend Developer",
                "Location: Baku",
                "Preferred work type: Remote",
                "5 years of experience",
                "Java",
                "Spring Boot",
                "PostgreSQL",
                "Kafka"
        );

        Files.writeString(
                extractionDirectory.resolve(
                        "notes.txt"
                ),
                "This file must be ignored"
        );

        JobExecution launchedExecution =
                jobOperator.start(
                        cvProcessingJob,
                        createJobParameters(
                                uploadId
                        )
                );

        assertThat(
                launchedExecution.getId()
        ).isPositive();

        JobExecution completedExecution =
                waitForJobCompletion(
                        launchedExecution.getId()
                );

        assertThat(
                completedExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        StepExecution stepExecution =
                findProcessingStep(
                        completedExecution
                );

        assertThat(
                stepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        assertThat(
                stepExecution.getReadCount()
        ).isEqualTo(
                2
        );

        assertThat(
                stepExecution.getWriteCount()
        ).isEqualTo(
                2
        );

        assertThat(
                stepExecution.getReadSkipCount()
        ).isZero();

        assertThat(
                stepExecution.getProcessSkipCount()
        ).isZero();

        assertThat(
                stepExecution.getWriteSkipCount()
        ).isZero();

        CvUpload completedUpload =
                cvUploadRepository
                        .findById(
                                uploadId
                        )
                        .orElseThrow();

        assertThat(
                completedUpload.getStatus()
        ).isEqualTo(
                UploadStatus.COMPLETED
        );

        assertThat(
                completedUpload.getTotalFiles()
        ).isEqualTo(
                2
        );

        assertThat(
                completedUpload.getProcessedFiles()
        ).isEqualTo(
                2
        );

        assertThat(
                completedUpload.getFailedFiles()
        ).isZero();

        assertThat(
                processingFailureRepository.count()
        ).isZero();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> {

                    List<Candidate> candidates =
                            candidateRepository
                                    .findAllByUpload_Id(
                                            uploadId
                                    );

                    assertThat(
                            candidates
                    ).hasSize(
                            2
                    );

                    assertThat(
                            candidates
                    )
                            .extracting(
                                    Candidate::getFullName
                            )
                            .containsExactlyInAnyOrder(
                                    "Jane Doe",
                                    "John Smith"
                            );

                    Candidate jane =
                            findCandidateByFilename(
                                    candidates,
                                    "jane.docx"
                            );

                    assertThat(
                            jane.getFullName()
                    ).isEqualTo(
                            "Jane Doe"
                    );

                    assertThat(
                            jane.getYearsOfExperience()
                    ).isEqualTo(
                            7
                    );

                    assertThat(
                            jane.getPreferredLocation()
                    ).isEqualTo(
                            "Baku"
                    );

                    assertThat(
                            jane.getPreferredJobType()
                    ).isEqualTo(
                            JobType.HYBRID
                    );

                    assertThat(
                            jane.getSkills()
                    ).containsExactlyInAnyOrder(
                            "Java",
                            "Spring Boot",
                            "Redis",
                            "Docker"
                    );

                    Candidate john =
                            findCandidateByFilename(
                                    candidates,
                                    "john.pdf"
                            );

                    assertThat(
                            john.getFullName()
                    ).isEqualTo(
                            "John Smith"
                    );

                    assertThat(
                            john.getYearsOfExperience()
                    ).isEqualTo(
                            5
                    );

                    assertThat(
                            john.getPreferredLocation()
                    ).isEqualTo(
                            "Baku"
                    );

                    assertThat(
                            john.getPreferredJobType()
                    ).isEqualTo(
                            JobType.REMOTE
                    );

                    assertThat(
                            john.getSkills()
                    ).containsExactlyInAnyOrder(
                            "Java",
                            "Spring Boot",
                            "PostgreSQL",
                            "Kafka"
                    );
                }
        );
    }

    @Test
    void shouldSkipInvalidDocumentAuditFailureAndCompleteWithErrors()
            throws Exception {

        CvUpload upload =
                new CvUpload(
                        "mixed-candidates.zip"
                );

        upload.registerDiscoveredFiles(
                3
        );

        cvUploadRepository.saveAndFlush(
                upload
        );

        UUID uploadId =
                upload.getId();

        assertThat(
                upload.getStatus()
        ).isEqualTo(
                UploadStatus.UPLOADED
        );

        Path extractionDirectory =
                uploadStorage
                        .extractionDirectory(
                                uploadId
                        );

        Files.createDirectories(
                extractionDirectory
        );

        TestDocumentFactory.createPdf(
                extractionDirectory.resolve(
                        "john.pdf"
                ),
                "John Smith",
                "Java Backend Developer",
                "Location: Baku",
                "Preferred work type: Remote",
                "5 years experience",
                "Java",
                "Spring Boot",
                "PostgreSQL"
        );

        TestDocumentFactory.createDocx(
                extractionDirectory.resolve(
                        "jane.docx"
                ),
                "Jane Doe",
                "Senior Java Developer",
                "Location: Baku",
                "Preferred work type: Hybrid",
                "7 years experience",
                "Java",
                "Spring Boot",
                "Redis"
        );

        Files.writeString(
                extractionDirectory.resolve(
                        "broken.pdf"
                ),
                """
                This is not a real PDF.
                It is only plain text.
                """
        );

        JobExecution launchedExecution =
                jobOperator.start(
                        cvProcessingJob,
                        createJobParameters(
                                uploadId
                        )
                );

        JobExecution completedExecution =
                waitForJobCompletion(
                        launchedExecution.getId()
                );

        assertThat(
                completedExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        StepExecution stepExecution =
                findProcessingStep(
                        completedExecution
                );

        assertThat(
                stepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        assertThat(
                stepExecution.getReadCount()
        ).isEqualTo(
                3
        );

        assertThat(
                stepExecution.getWriteCount()
        ).isEqualTo(
                2
        );

        assertThat(
                stepExecution.getReadSkipCount()
        ).isZero();

        assertThat(
                stepExecution.getProcessSkipCount()
        ).isEqualTo(
                1
        );

        assertThat(
                stepExecution.getWriteSkipCount()
        ).isZero();

        List<Candidate> candidates =
                candidateRepository
                        .findAllByUpload_Id(
                                uploadId
                        );

        assertThat(
                candidates
        ).hasSize(
                2
        );

        assertThat(
                candidates
        )
                .extracting(
                        Candidate::getSourceFilename
                )
                .containsExactlyInAnyOrder(
                        "john.pdf",
                        "jane.docx"
                );

        assertThat(
                candidates
        )
                .extracting(
                        Candidate::getSourceFilename
                )
                .doesNotContain(
                        "broken.pdf"
                );

        List<ProcessingFailure> failures =
                processingFailureRepository
                        .findAll();

        assertThat(
                failures
        ).hasSize(
                1
        );

        ProcessingFailure failure =
                failures.getFirst();

        assertThat(
                failure.getId()
        ).isNotNull();

        assertThat(
                failure.getUpload()
                        .getId()
        ).isEqualTo(
                uploadId
        );

        assertThat(
                failure.getFilename()
        ).isEqualTo(
                "broken.pdf"
        );

        assertThat(
                failure.getErrorCode()
        ).isEqualTo(
                "UNSUPPORTED_MEDIA_TYPE"
        );

        assertThat(
                failure.getErrorMessage()
        )
                .isNotBlank()
                .contains(
                        "text/plain"
                );

        assertThat(
                failure.getCreatedAt()
        ).isNotNull();

        CvUpload completedUpload =
                cvUploadRepository
                        .findById(
                                uploadId
                        )
                        .orElseThrow();

        assertThat(
                completedUpload.getTotalFiles()
        ).isEqualTo(
                3
        );

        assertThat(
                completedUpload.getProcessedFiles()
        ).isEqualTo(
                2
        );

        assertThat(
                completedUpload.getFailedFiles()
        ).isEqualTo(
                1
        );

        assertThat(
                completedUpload.getStatus()
        ).isEqualTo(
                UploadStatus.COMPLETED_WITH_ERRORS
        );
    }

    private JobParameters createJobParameters(
            UUID uploadId
    ) {

        return new JobParametersBuilder()
                .addString(
                        "uploadId",
                        uploadId.toString()
                )
                .toJobParameters();
    }

    private StepExecution findProcessingStep(
            JobExecution jobExecution
    ) {

        return jobExecution
                .getStepExecutions()
                .stream()
                .filter(
                        stepExecution ->
                                STEP_NAME.equals(
                                        stepExecution.getStepName()
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Step not found: "
                                                + STEP_NAME
                                )
                );
    }

    private Candidate findCandidateByFilename(
            List<Candidate> candidates,
            String filename
    ) {

        return candidates
                .stream()
                .filter(
                        candidate ->
                                filename.equals(
                                        candidate.getSourceFilename()
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Candidate not found for source file: "
                                                + filename
                                )
                );
    }

    private JobExecution waitForJobCompletion(
            long executionId
    ) throws InterruptedException {

        Instant deadline =
                Instant.now()
                        .plus(
                                JOB_TIMEOUT
                        );

        while (
                Instant.now()
                        .isBefore(
                                deadline
                        )
        ) {

            JobExecution currentExecution =
                    jobRepository
                            .getJobExecution(
                                    executionId
                            );

            if (
                    currentExecution != null
                            && !currentExecution
                            .getStatus()
                            .isRunning()
            ) {

                return currentExecution;
            }

            Thread.sleep(
                    50
            );
        }

        throw new AssertionError(
                "Timed out after "
                        + JOB_TIMEOUT.toSeconds()
                        + " seconds waiting for job execution "
                        + executionId
                        + " to complete"
        );
    }

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-batch-it-"
            );

        } catch (
                IOException exception
        ) {

            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }

    private static void clearStorage()
            throws IOException {

        if (
                Files.notExists(
                        STORAGE_ROOT
                )
        ) {

            Files.createDirectories(
                    STORAGE_ROOT
            );

            return;
        }

        try (
                var paths =
                        Files.walk(
                                STORAGE_ROOT
                        )
        ) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .filter(
                            path ->
                                    !path.equals(
                                            STORAGE_ROOT
                                    )
                    )
                    .forEach(
                            path -> {

                                try {

                                    Files.deleteIfExists(
                                            path
                                    );

                                } catch (
                                        IOException exception
                                ) {

                                    throw new RuntimeException(
                                            exception
                                    );
                                }
                            }
                    );
        }
    }

    @AfterAll
    static void cleanupStorage()
            throws IOException {

        if (
                Files.notExists(
                        STORAGE_ROOT
                )
        ) {
            return;
        }

        try (
                var paths =
                        Files.walk(
                                STORAGE_ROOT
                        )
        ) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(
                            path -> {

                                try {

                                    Files.deleteIfExists(
                                            path
                                    );

                                } catch (
                                        IOException ignored
                                ) {
                                }
                            }
                    );
        }
    }
}
