package com.adil.cvscanner.upload.api;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.processing.batch.CvFileItemReader;
import com.adil.cvscanner.processing.batch.CvProcessingJobListener;
import com.adil.cvscanner.processing.batch.CvProcessingProgressListener;
import com.adil.cvscanner.security.SecurityTestUsers;
import com.adil.cvscanner.upload.application.CvUploadStatusService;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(
        CvUploadLiveProgressIT.LiveProgressTestConfiguration.class
)
class CvUploadLiveProgressIT {

    private static final String STEP_NAME =
            "liveProgressTestStep";

    private static final Duration JOB_TIMEOUT =
            Duration.ofSeconds(15);

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
                            "cvscanner_live_progress_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @Autowired
    @Qualifier("liveProgressTestJob")
    private Job liveProgressTestJob;

    @Autowired
    private BlockingProgressProcessor
            blockingProgressProcessor;

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

        registry.add(
                "app.batch.retry.max-retries",
                () -> "2"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    @BeforeEach
    void cleanBeforeTest() {

        cvUploadRepository.deleteAll();

        blockingProgressProcessor.reset();
    }

    /*
     * ============================================================
     * MAIN TEST
     * ============================================================
     *
     * 4 item
     *
     * chunk-size = 2
     *
     * İlk chunk commit olduqdan sonra
     * ikinci chunk-un ilk item-ində
     * processor bloklanır.
     *
     * Həmin anda HTTP status endpoint-ə
     * request göndəririk.
     */

    @Test
    void shouldExposeCommittedProgressWhileJobIsStillRunning()
            throws Exception {

        /*
         * =====================================================
         * 1. CREATE UPLOAD
         * =====================================================
         */

        CvUpload upload =
                new CvUpload(
                        "live-progress.zip"
                );

        upload.registerDiscoveredFiles(
                4
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

        /*
         * =====================================================
         * 2. START ASYNC TEST JOB
         * =====================================================
         */

        JobExecution launchedExecution =
                jobOperator.start(
                        liveProgressTestJob,
                        createJobParameters(
                                uploadId
                        )
                );

        assertThat(
                launchedExecution.getId()
        ).isPositive();

        /*
         * =====================================================
         * 3. WAIT UNTIL SECOND CHUNK STARTS
         * =====================================================
         *
         * Processor item #3-ə çatanda:
         *
         * item #1 + #2 olan ilk chunk
         * artıq tamamlanıb və commit olub.
         *
         * Amma ikinci chunk hələ bitməyib.
         */

        boolean secondChunkStarted =
                blockingProgressProcessor
                        .awaitSecondChunkStarted(
                                10,
                                TimeUnit.SECONDS
                        );

        assertThat(
                secondChunkStarted
        )
                .as(
                        "Second chunk did not start within timeout"
                )
                .isTrue();

        /*
         * =====================================================
         * 4. VERIFY DATABASE MID-JOB
         * =====================================================
         */

        CvUpload processingUpload =
                cvUploadRepository
                        .findById(
                                uploadId
                        )
                        .orElseThrow();

        assertThat(
                processingUpload.getStatus()
        ).isEqualTo(
                UploadStatus.PROCESSING
        );

        assertThat(
                processingUpload.getTotalFiles()
        ).isEqualTo(
                4
        );

        /*
         * İlk chunk = 2 item.
         */

        assertThat(
                processingUpload.getProcessedFiles()
        ).isEqualTo(
                2
        );

        assertThat(
                processingUpload.getFailedFiles()
        ).isZero();

        assertThat(
                processingUpload.getCompletedAt()
        ).isNull();

        /*
         * =====================================================
         * 5. REAL HTTP REQUEST WHILE JOB IS RUNNING
         * =====================================================
         */

        mockMvc.perform(
                        get(
                                "/api/v1/uploads/{uploadId}",
                                uploadId
                        )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.uploadId"
                        ).value(
                                uploadId.toString()
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.filename"
                        ).value(
                                "live-progress.zip"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "PROCESSING"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalFiles"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.processedFiles"
                        ).value(
                                2
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.failedFiles"
                        ).value(
                                0
                        )
                );

        /*
         * =====================================================
         * 6. RELEASE SECOND CHUNK
         * =====================================================
         *
         * try/finally vacibdir.
         *
         * Assertion fail olsa belə job thread-i
         * bloklanmış vəziyyətdə qoymuruq.
         */

        blockingProgressProcessor
                .release();

        /*
         * =====================================================
         * 7. WAIT FOR FINAL COMPLETION
         * =====================================================
         */

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
                findStepExecution(
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
                4
        );

        assertThat(
                stepExecution.getWriteCount()
        ).isEqualTo(
                4
        );

        /*
         * =====================================================
         * 8. FINAL DATABASE RESULT
         * =====================================================
         */

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
                4
        );

        assertThat(
                completedUpload.getProcessedFiles()
        ).isEqualTo(
                4
        );

        assertThat(
                completedUpload.getFailedFiles()
        ).isZero();

        assertThat(
                completedUpload.getCompletedAt()
        ).isNotNull();

        /*
         * =====================================================
         * 9. FINAL HTTP RESULT
         * =====================================================
         */

        mockMvc.perform(
                        get(
                                "/api/v1/uploads/{uploadId}",
                                uploadId
                        )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "COMPLETED"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalFiles"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.processedFiles"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.failedFiles"
                        ).value(
                                0
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.completedAt"
                        ).isNotEmpty()
                );
    }

    /*
     * ============================================================
     * PARAMETERS
     * ============================================================
     */

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

    /*
     * ============================================================
     * WAIT
     * ============================================================
     */

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

            JobExecution execution =
                    jobRepository
                            .getJobExecution(
                                    executionId
                            );

            if (
                    execution != null
                            &&
                            !execution
                                    .getStatus()
                                    .isRunning()
            ) {

                return execution;
            }

            Thread.sleep(
                    50
            );
        }

        /*
         * Timeout olsa test JVM-ni bloklamayaq.
         */

        blockingProgressProcessor
                .release();

        throw new AssertionError(
                "Timed out waiting for job execution: "
                        + executionId
        );
    }

    /*
     * ============================================================
     * STEP LOOKUP
     * ============================================================
     */

    private StepExecution findStepExecution(
            JobExecution jobExecution
    ) {

        return jobExecution
                .getStepExecutions()
                .stream()
                .filter(
                        stepExecution ->
                                STEP_NAME.equals(
                                        stepExecution
                                                .getStepName()
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

    /*
     * ============================================================
     * TEMP STORAGE
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-live-progress-it-"
            );

        } catch (
                IOException exception
        ) {

            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     */

    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class LiveProgressTestConfiguration {

        /*
         * ========================================================
         * READER
         * ========================================================
         *
         * Production CvFileItemReader-dən
         * istifadə edirik.
         *
         * Real filesystem parse etmək bu testin
         * məqsədi deyil.
         */

        @Bean("liveProgressTestReader")
        @StepScope
        CvFileItemReader liveProgressTestReader() {

            return new CvFileItemReader(
                    List.of(
                            Path.of(
                                    "01.pdf"
                            ),
                            Path.of(
                                    "02.pdf"
                            ),
                            Path.of(
                                    "03.pdf"
                            ),
                            Path.of(
                                    "04.pdf"
                            )
                    )
            );
        }

        /*
         * ========================================================
         * BLOCKING PROCESSOR
         * ========================================================
         */

        @Bean
        BlockingProgressProcessor
        blockingProgressProcessor() {

            return new BlockingProgressProcessor();
        }

        /*
         * ========================================================
         * WRITER
         * ========================================================
         *
         * Bu dedicated testdə Candidate DB-yə
         * yazmaq lazım deyil.
         *
         * Writer uğurlu hesab olunur və
         * ChunkListener progress-i update edir.
         */

        @Bean("liveProgressTestWriter")
        ItemWriter<CandidateDraft>
        liveProgressTestWriter() {

            return chunk -> {
                // intentional no-op
            };
        }

        /*
         * ========================================================
         * PRODUCTION PROGRESS LISTENER
         * ========================================================
         *
         * Test üçün yeni implementation
         * yazmırıq.
         *
         * Məhz production
         * CvProcessingProgressListener-i
         * istifadə edirik.
         */

        @Bean("liveProgressTestListener")
        @StepScope
        CvProcessingProgressListener
        liveProgressTestListener(
                @Value("#{jobParameters['uploadId']}")
                String uploadId,
                CvUploadStatusService
                        uploadStatusService
        ) {

            return new CvProcessingProgressListener(
                    UUID.fromString(
                            uploadId
                    ),
                    uploadStatusService
            );
        }

        /*
         * ========================================================
         * STEP
         * ========================================================
         *
         * chunk-size = 2
         */

        @Bean("liveProgressTestStep")
        Step liveProgressTestStep(
                JobRepository jobRepository,
                org.springframework.transaction.PlatformTransactionManager
                        transactionManager,
                @Qualifier("liveProgressTestReader")
                CvFileItemReader reader,
                BlockingProgressProcessor processor,
                @Qualifier("liveProgressTestWriter")
                ItemWriter<CandidateDraft> writer,
                @Qualifier("liveProgressTestListener")
                CvProcessingProgressListener
                        progressListener
        ) {

            return new ChunkOrientedStepBuilder
                    <Path, CandidateDraft>(
                    STEP_NAME,
                    jobRepository,
                    2
            )
                    .transactionManager(
                            transactionManager
                    )
                    .reader(
                            reader
                    )
                    .processor(
                            processor
                    )
                    .writer(
                            writer
                    )
                    .listener(
                            progressListener
                    )
                    .build();
        }

        /*
         * ========================================================
         * JOB
         * ========================================================
         *
         * Production CvProcessingJobListener
         * istifadə olunur:
         *
         * beforeJob → PROCESSING
         * afterJob  → COMPLETED
         */

        @Bean("liveProgressTestJob")
        Job liveProgressTestJob(
                JobRepository jobRepository,
                @Qualifier("liveProgressTestStep")
                Step step,
                CvProcessingJobListener
                        cvProcessingJobListener
        ) {

            return new JobBuilder(
                    "liveProgressTestJob",
                    jobRepository
            )
                    .listener(
                            cvProcessingJobListener
                    )
                    .start(
                            step
                    )
                    .build();
        }
    }

    /*
     * ============================================================
     * BLOCKING PROCESSOR
     * ============================================================
     *
     * #1 → normal
     * #2 → normal
     *
     * ilk chunk commit
     *
     * #3 → test burada block edilir
     */

    static class BlockingProgressProcessor
            implements ItemProcessor<Path, CandidateDraft> {

        private final AtomicInteger
                processedInvocations =
                new AtomicInteger();

        private volatile CountDownLatch
                secondChunkStarted;

        private volatile CountDownLatch
                releaseSecondChunk;

        BlockingProgressProcessor() {

            reset();
        }

        @Override
        public CandidateDraft process(
                Path item
        ) throws Exception {

            int invocation =
                    processedInvocations
                            .incrementAndGet();

            /*
             * Item #3 ikinci chunk-un
             * ilk item-idir.
             */

            if (
                    invocation == 3
            ) {

                secondChunkStarted
                        .countDown();

                boolean released =
                        releaseSecondChunk
                                .await(
                                        10,
                                        TimeUnit.SECONDS
                                );

                if (!released) {

                    throw new IllegalStateException(
                            "Timed out waiting for live-progress test release"
                    );
                }
            }

            return new CandidateDraft(
                    "Candidate "
                            + invocation,
                    invocation,
                    "Baku",
                    JobType.UNKNOWN,
                    Set.of(
                            "Java"
                    ),
                    item
                            .getFileName()
                            .toString()
            );
        }

        boolean awaitSecondChunkStarted(
                long timeout,
                TimeUnit unit
        ) throws InterruptedException {

            return secondChunkStarted
                    .await(
                            timeout,
                            unit
                    );
        }

        void release() {

            releaseSecondChunk
                    .countDown();
        }

        synchronized void reset() {

            processedInvocations.set(
                    0
            );

            secondChunkStarted =
                    new CountDownLatch(
                            1
                    );

            releaseSecondChunk =
                    new CountDownLatch(
                            1
                    );
        }
    }
}