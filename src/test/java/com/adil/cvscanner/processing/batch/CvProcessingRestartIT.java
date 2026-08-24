package com.adil.cvscanner.processing.batch;

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
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(
        CvProcessingRestartIT.RestartTestConfiguration.class
)
class CvProcessingRestartIT {

    private static final String STEP_NAME =
            "cvProcessingRestartTestStep";

    private static final Duration JOB_TIMEOUT =
            Duration.ofSeconds(15);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_restart_test"
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
    private JobRepository jobRepository;

    @Autowired
    @Qualifier("cvProcessingRestartTestJob")
    private Job restartTestJob;

    @Autowired
    private RestartProbeWriter restartProbeWriter;

    @BeforeEach
    void resetWriter() {

        restartProbeWriter.reset();
    }

    /*
     * ============================================================
     * MAIN TEST
     * ============================================================
     *
     * Chunk size = 2
     *
     * Files:
     *
     * 01.pdf
     * 02.pdf
     * 03.pdf
     * 04.pdf
     * 05.pdf
     *
     *
     * First execution:
     *
     * chunk 1
     * 01 + 02
     *      ↓
     * COMMIT
     *
     * chunk 2
     * 03 + 04
     *      ↓
     * FAIL
     *
     *
     * Restart:
     *
     * reader checkpoint = index 2
     *
     * 03 + 04
     *      ↓
     * SUCCESS
     *
     * 05
     *      ↓
     * SUCCESS
     */

    @Test
    void shouldRestartFailedJobFromLastCommittedCheckpoint()
            throws Exception {

        /*
         * =====================================================
         * 1. FIRST EXECUTION
         * =====================================================
         */

        JobExecution launchedExecution =
                jobOperator.start(
                        restartTestJob,
                        uniqueJobParameters()
                );

        JobExecution failedExecution =
                waitForJobCompletion(
                        launchedExecution.getId()
                );

        /*
         * Writer ikinci chunk-da intentional
         * exception atdığı üçün job fail
         * olmalıdır.
         */
        assertThat(
                failedExecution.getStatus()
        ).isEqualTo(
                BatchStatus.FAILED
        );

        StepExecution failedStepExecution =
                findStepExecution(
                        failedExecution
                );

        assertThat(
                failedStepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.FAILED
        );

        /*
         * İlk chunk commit olub.
         *
         * Ona görə writer-in real successful
         * business side-effect-i:
         *
         * 01.pdf
         * 02.pdf
         *
         * olmalıdır.
         */
        assertThat(
                restartProbeWriter
                        .getSuccessfullyWrittenFiles()
        ).containsExactly(
                "01.pdf",
                "02.pdf"
        );

        /*
         * Writer invocation history:
         *
         * chunk #1 → success
         * chunk #2 → fail
         */
        assertThat(
                restartProbeWriter
                        .getInvokedChunks()
        ).containsExactly(
                List.of(
                        "01.pdf",
                        "02.pdf"
                ),
                List.of(
                        "03.pdf",
                        "04.pdf"
                )
        );

        /*
         * =====================================================
         * 2. RESTART
         * =====================================================
         *
         * ÇOX VACİB:
         *
         * bunu etmirik:
         *
         * jobOperator.start(...)
         *
         *
         * Failed execution-u restart edirik.
         */

        JobExecution restartedExecution =
                jobOperator.restart(
                        failedExecution
                );

        /*
         * Restart yeni JobExecution yaradır.
         */
        assertThat(
                restartedExecution.getId()
        ).isNotEqualTo(
                failedExecution.getId()
        );

        /*
         * Amma JobInstance eyni qalmalıdır.
         *
         * Yəni:
         *
         * Execution #1 FAILED
         * Execution #2 COMPLETED
         *
         * eyni logical job instance üçündür.
         */
        assertThat(
                restartedExecution
                        .getJobInstance()
                        .getId()
        ).isEqualTo(
                failedExecution
                        .getJobInstance()
                        .getId()
        );

        /*
         * Parameters də həmin logical
         * JobInstance üçündür.
         */
        assertThat(
                restartedExecution
                        .getJobParameters()
        ).isEqualTo(
                failedExecution
                        .getJobParameters()
        );

        /*
         * =====================================================
         * 3. WAIT FOR RESTART
         * =====================================================
         */

        JobExecution completedExecution =
                waitForJobCompletion(
                        restartedExecution.getId()
                );

        assertThat(
                completedExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        StepExecution restartedStepExecution =
                findStepExecution(
                        completedExecution
                );

        assertThat(
                restartedStepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        /*
         * Restart execution yalnız qalan:
         *
         * 03
         * 04
         * 05
         *
         * item-lərini oxumalıdır.
         *
         * 01 və 02 artıq əvvəlki committed
         * checkpoint-dən əvvəldədir.
         */
        assertThat(
                restartedStepExecution
                        .getReadCount()
        ).isEqualTo(
                3
        );

        assertThat(
                restartedStepExecution
                        .getWriteCount()
        ).isEqualTo(
                3
        );

        /*
         * =====================================================
         * 4. FINAL BUSINESS RESULT
         * =====================================================
         */

        assertThat(
                restartProbeWriter
                        .getSuccessfullyWrittenFiles()
        ).containsExactly(
                "01.pdf",
                "02.pdf",
                "03.pdf",
                "04.pdf",
                "05.pdf"
        );

        /*
         * Əgər reader restart zamanı index=0-dan
         * başlasaydı:
         *
         * 01.pdf
         * 02.pdf
         *
         * ikinci dəfə writer-a gələcəkdi.
         *
         * Amma gəlməməlidir.
         */
        assertThat(
                countOccurrences(
                        restartProbeWriter
                                .getSuccessfullyWrittenFiles(),
                        "01.pdf"
                )
        ).isEqualTo(
                1
        );

        assertThat(
                countOccurrences(
                        restartProbeWriter
                                .getSuccessfullyWrittenFiles(),
                        "02.pdf"
                )
        ).isEqualTo(
                1
        );

        /*
         * =====================================================
         * 5. COMPLETE WRITER HISTORY
         * =====================================================
         *
         * First execution:
         *
         * [01, 02] SUCCESS
         * [03, 04] FAIL
         *
         * Restart:
         *
         * [03, 04] SUCCESS
         * [05]     SUCCESS
         */

        assertThat(
                restartProbeWriter
                        .getInvokedChunks()
        ).containsExactly(
                List.of(
                        "01.pdf",
                        "02.pdf"
                ),
                List.of(
                        "03.pdf",
                        "04.pdf"
                ),
                List.of(
                        "03.pdf",
                        "04.pdf"
                ),
                List.of(
                        "05.pdf"
                )
        );
    }

    /*
     * ============================================================
     * JOB PARAMETERS
     * ============================================================
     */

    private JobParameters uniqueJobParameters() {

        /*
         * Bu yalnız dedicated test job üçündür.
         *
         * Production cvProcessingJob-da
         * identifying parameter uploadId-dir
         * və random run.id istifadə etmirik.
         */
        return new JobParametersBuilder()
                .addString(
                        "testRunId",
                        UUID.randomUUID()
                                .toString()
                )
                .toJobParameters();
    }

    /*
     * ============================================================
     * ASYNC WAIT
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
     * OCCURRENCE COUNT
     * ============================================================
     */

    private long countOccurrences(
            List<String> files,
            String filename
    ) {

        return files
                .stream()
                .filter(
                        filename::equals
                )
                .count();
    }

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     */

    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class RestartTestConfiguration {

        /*
         * ========================================================
         * READER
         * ========================================================
         *
         * Burada real production
         * CvFileItemReader-dən istifadə edirik.
         *
         * Əsas məqsəd onun ExecutionContext
         * restart state-ni test etməkdir.
         */

        @Bean("cvProcessingRestartTestReader")
        @StepScope
        CvFileItemReader cvProcessingRestartTestReader() {

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
                            ),
                            Path.of(
                                    "05.pdf"
                            )
                    )
            );
        }

        /*
         * ========================================================
         * PROCESSOR
         * ========================================================
         *
         * Restart testində parsing/extraction
         * business logic lazım deyil.
         *
         * Path olduğu kimi keçir.
         */

        @Bean("cvProcessingRestartTestProcessor")
        ItemProcessor<Path, Path>
        cvProcessingRestartTestProcessor() {

            return path -> path;
        }

        /*
         * ========================================================
         * WRITER
         * ========================================================
         */

        @Bean
        RestartProbeWriter restartProbeWriter() {

            return new RestartProbeWriter();
        }

        /*
         * ========================================================
         * STEP
         * ========================================================
         */

        @Bean("cvProcessingRestartTestStep")
        Step cvProcessingRestartTestStep(
                JobRepository jobRepository,
                PlatformTransactionManager
                        transactionManager,
                @Qualifier(
                        "cvProcessingRestartTestReader"
                )
                CvFileItemReader reader,
                @Qualifier(
                        "cvProcessingRestartTestProcessor"
                )
                ItemProcessor<Path, Path> processor,
                RestartProbeWriter writer
        ) {

            return new ChunkOrientedStepBuilder
                    <Path, Path>(
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

                    /*
                     * Qəsdən faultTolerant()
                     * əlavə etmirik.
                     *
                     * Writer exception atanda
                     * həmin execution dərhal
                     * FAILED olsun.
                     */
                    .build();
        }

        /*
         * ========================================================
         * JOB
         * ========================================================
         */

        @Bean("cvProcessingRestartTestJob")
        Job cvProcessingRestartTestJob(
                JobRepository jobRepository,
                @Qualifier(
                        "cvProcessingRestartTestStep"
                )
                Step step
        ) {

            return new JobBuilder(
                    "cvProcessingRestartTestJob",
                    jobRepository
            )
                    .start(
                            step
                    )
                    .build();
        }
    }

    /*
     * ============================================================
     * RESTART PROBE WRITER
     * ============================================================
     *
     * Məqsəd:
     *
     * [01, 02] → success
     * [03, 04] → yalnız İLK DƏFƏ fail
     *
     * restart-dan sonra:
     *
     * [03, 04] → success
     * [05]     → success
     */

    static class RestartProbeWriter
            implements ItemWriter<Path> {

        /*
         * Yalnız bir dəfə failure yaradırıq.
         */
        private final AtomicBoolean failOnce =
                new AtomicBoolean(
                        true
                );

        /*
         * Writer-a daxil olmuş bütün chunk-lar.
         *
         * Failed chunk da burada görünür.
         */
        private final List<List<String>>
                invokedChunks =
                new ArrayList<>();

        /*
         * Yalnız həqiqətən uğurlu writer
         * invocation-larının item-ləri.
         */
        private final List<String>
                successfullyWrittenFiles =
                new ArrayList<>();

        @Override
        public synchronized void write(
                Chunk<? extends Path> chunk
        ) {

            List<String> filenames =
                    chunk
                            .getItems()
                            .stream()
                            .map(
                                    path ->
                                            path
                                                    .getFileName()
                                                    .toString()
                            )
                            .toList();

            /*
             * Invocation history-yə failed
             * chunk da daxil edilir.
             */
            invokedChunks.add(
                    List.copyOf(
                            filenames
                    )
            );

            /*
             * İlk dəfə chunk daxilində
             * 03.pdf görəndə job-u
             * intentionally fail edirik.
             */
            if (
                    filenames.contains(
                            "03.pdf"
                    )
                            && failOnce.compareAndSet(
                            true,
                            false
                    )
            ) {

                throw new IllegalStateException(
                        "Simulated writer failure after first committed chunk"
                );
            }

            /*
             * Exception yoxdursa business
             * write uğurludur.
             */
            successfullyWrittenFiles
                    .addAll(
                            filenames
                    );
        }

        synchronized List<List<String>>
        getInvokedChunks() {

            return invokedChunks
                    .stream()
                    .map(
                            List::copyOf
                    )
                    .toList();
        }

        synchronized List<String>
        getSuccessfullyWrittenFiles() {

            return List.copyOf(
                    successfullyWrittenFiles
            );
        }

        synchronized void reset() {

            failOnce.set(
                    true
            );

            invokedChunks.clear();

            successfullyWrittenFiles.clear();
        }
    }
}