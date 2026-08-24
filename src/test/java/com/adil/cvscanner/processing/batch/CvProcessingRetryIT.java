package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.application.CandidateExtractionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.BatchStatus;
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
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(
        CvProcessingRetryIT.RetryTestConfiguration.class
)
@TestPropertySource(
        properties = {
                /*
                 * Production-da delay 500ms-dir.
                 *
                 * Testdə lazımsız gözləməmək üçün
                 * retry davranışını dəyişmədən
                 * delay-i sıfırlayırıq.
                 */
                "app.batch.retry.max-retries=2",
                "app.batch.retry.delay=0ms"
        }
)
class CvProcessingRetryIT {

    private static final Duration JOB_TIMEOUT =
            Duration.ofSeconds(15);

    private static final String TRANSIENT_STEP_NAME =
            "transientRetryTestStep";

    private static final String NON_RETRYABLE_STEP_NAME =
            "nonRetryableTestStep";

    /*
     * Real Spring Batch metadata PostgreSQL-da
     * saxlanacaq.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_retry_test"
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
    @Qualifier("transientRetryTestJob")
    private Job transientRetryTestJob;

    @Autowired
    @Qualifier("nonRetryableTestJob")
    private Job nonRetryableTestJob;

    @Autowired
    private TransientRetryProbeWriter
            transientRetryProbeWriter;

    @Autowired
    private NonRetryableProbeWriter
            nonRetryableProbeWriter;

    /*
     * ============================================================
     * TEST 1
     * TRANSIENT ERROR MUST BE RETRIED
     * ============================================================
     *
     * attempt #1 -> transient DB error
     * attempt #2 -> transient DB error
     * attempt #3 -> success
     *
     * expected:
     *
     * attempts = 3
     * Job      = COMPLETED
     * Step     = COMPLETED
     * write    = 1
     */

    @Test
    void shouldRetryTransientDataAccessFailureAndEventuallyComplete()
            throws Exception {

        transientRetryProbeWriter.reset();

        /*
         * Test JobInstance hər test run-da
         * unikal olsun.
         */
        JobParameters parameters =
                uniqueJobParameters();

        JobExecution launchedExecution =
                jobOperator.start(
                        transientRetryTestJob,
                        parameters
                );

        JobExecution completedExecution =
                waitForJobCompletion(
                        launchedExecution.getId()
                );

        /*
         * İlk iki write fail olsa da
         * üçüncü invocation uğurlu olduğu üçün
         * bütün job tamamlanmalıdır.
         */
        assertThat(
                completedExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        StepExecution stepExecution =
                findStep(
                        completedExecution,
                        TRANSIENT_STEP_NAME
                );

        assertThat(
                stepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.COMPLETED
        );

        /*
         * Reader yalnız bir business item verir.
         */
        assertThat(
                stepExecution.getReadCount()
        ).isEqualTo(
                1
        );

        /*
         * Commit olunan real write sayı 1-dir.
         *
         * İlk iki invocation rollback/retry-dir.
         */
        assertThat(
                stepExecution.getWriteCount()
        ).isEqualTo(
                1
        );

        /*
         * Əsas assertion:
         *
         * maxRetries = 2
         *
         * 1 initial
         * +
         * 2 retries
         *
         * = 3 writer invocation
         */
        assertThat(
                transientRetryProbeWriter
                        .getAttempts()
        ).isEqualTo(
                3
        );

        /*
         * Uğurlu final invocation-da
         * item həqiqətən writer tərəfindən
         * qəbul olunub.
         */
        assertThat(
                transientRetryProbeWriter
                        .getSuccessfullyWrittenItems()
        ).containsExactly(
                "candidate-1"
        );
    }

    /*
     * ============================================================
     * TEST 2
     * NON-TRANSIENT BUSINESS ERROR MUST NOT BE RETRIED
     * ============================================================
     *
     * CandidateExtractionException
     *
     * transient DB exception deyil.
     *
     * Bu dedicated test step-də skip də
     * configure etmirik.
     *
     * Ona görə:
     *
     * attempt #1 -> fail
     *
     * RETRY YOX
     *
     * Job = FAILED
     */

    @Test
    void shouldNotRetryNonTransientBusinessFailure()
            throws Exception {

        nonRetryableProbeWriter.reset();

        JobExecution launchedExecution =
                jobOperator.start(
                        nonRetryableTestJob,
                        uniqueJobParameters()
                );

        JobExecution failedExecution =
                waitForJobCompletion(
                        launchedExecution.getId()
                );

        assertThat(
                failedExecution.getStatus()
        ).isEqualTo(
                BatchStatus.FAILED
        );

        StepExecution stepExecution =
                findStep(
                        failedExecution,
                        NON_RETRYABLE_STEP_NAME
                );

        assertThat(
                stepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.FAILED
        );

        /*
         * RetryPolicy yalnız
         * TransientDataAccessException
         * hierarchy-sinə icazə verdiyi üçün
         * CandidateExtractionException
         * ikinci dəfə çağırılmamalıdır.
         */
        assertThat(
                nonRetryableProbeWriter
                        .getAttempts()
        ).isEqualTo(
                1
        );
    }

    /*
     * ============================================================
     * JOB PARAMETERS
     * ============================================================
     */

    private JobParameters uniqueJobParameters() {

        /*
         * Bu yalnız TEST job-ları üçündür.
         *
         * Production cvProcessingJob üçün
         * run.id əlavə etmirik.
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

    private StepExecution findStep(
            JobExecution jobExecution,
            String stepName
    ) {

        return jobExecution
                .getStepExecutions()
                .stream()
                .filter(
                        stepExecution ->
                                stepName.equals(
                                        stepExecution
                                                .getStepName()
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Step not found: "
                                                + stepName
                                )
                );
    }

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     *
     * Production cvProcessingJob-a toxunmuruq.
     *
     * Sadəcə retry mexanizmini isolated şəkildə
     * real Spring Batch Step içində test edən
     * iki xüsusi Job yaradırıq.
     */

    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class RetryTestConfiguration {

        /*
         * ========================================================
         * TRANSIENT WRITER
         * ========================================================
         */

        @Bean
        TransientRetryProbeWriter
        transientRetryProbeWriter() {

            return new TransientRetryProbeWriter();
        }

        /*
         * ========================================================
         * NON-RETRYABLE WRITER
         * ========================================================
         */

        @Bean
        NonRetryableProbeWriter
        nonRetryableProbeWriter() {

            return new NonRetryableProbeWriter();
        }

        /*
         * ========================================================
         * TRANSIENT RETRY STEP
         * ========================================================
         */

        @Bean("transientRetryTestStep")
        Step transientRetryTestStep(
                JobRepository jobRepository,
                PlatformTransactionManager
                        transactionManager,
                @Qualifier("cvProcessingRetryPolicy")
                RetryPolicy retryPolicy,
                TransientRetryProbeWriter writer
        ) {

            /*
             * ListItemReader Spring Batch-də
             * testing üçün nəzərdə tutulmuş
             * sadə ItemReader implementation-dır.
             */
            ListItemReader<String> reader =
                    new ListItemReader<>(
                            List.of(
                                    "candidate-1"
                            )
                    );

            ItemProcessor<String, String>
                    processor =
                    item -> item;

            return new ChunkOrientedStepBuilder
                    <String, String>(
                    TRANSIENT_STEP_NAME,
                    jobRepository,
                    1
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

                    .faultTolerant()

                    /*
                     * Production-da istifadə etdiyimiz
                     * EYNİ RetryPolicy bean.
                     */
                    .retryPolicy(
                            retryPolicy
                    )

                    .build();
        }

        /*
         * ========================================================
         * TRANSIENT RETRY JOB
         * ========================================================
         */

        @Bean("transientRetryTestJob")
        Job transientRetryTestJob(
                JobRepository jobRepository,
                @Qualifier("transientRetryTestStep")
                Step step
        ) {

            return new JobBuilder(
                    "transientRetryTestJob",
                    jobRepository
            )
                    .start(
                            step
                    )
                    .build();
        }

        /*
         * ========================================================
         * NON-RETRYABLE STEP
         * ========================================================
         */

        @Bean("nonRetryableTestStep")
        Step nonRetryableTestStep(
                JobRepository jobRepository,
                PlatformTransactionManager
                        transactionManager,
                @Qualifier("cvProcessingRetryPolicy")
                RetryPolicy retryPolicy,
                NonRetryableProbeWriter writer
        ) {

            ListItemReader<String> reader =
                    new ListItemReader<>(
                            List.of(
                                    "candidate-1"
                            )
                    );

            ItemProcessor<String, String>
                    processor =
                    item -> item;

            return new ChunkOrientedStepBuilder
                    <String, String>(
                    NON_RETRYABLE_STEP_NAME,
                    jobRepository,
                    1
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

                    .faultTolerant()

                    /*
                     * Yenə production
                     * RetryPolicy-dən istifadə
                     * edirik.
                     *
                     * Amma writer transient
                     * exception atmayacaq.
                     */
                    .retryPolicy(
                            retryPolicy
                    )

                    .build();
        }

        /*
         * ========================================================
         * NON-RETRYABLE JOB
         * ========================================================
         */

        @Bean("nonRetryableTestJob")
        Job nonRetryableTestJob(
                JobRepository jobRepository,
                @Qualifier("nonRetryableTestStep")
                Step step
        ) {

            return new JobBuilder(
                    "nonRetryableTestJob",
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
     * TRANSIENT RETRY PROBE WRITER
     * ============================================================
     *
     * Burada real DB-ni sındırmağa çalışmırıq.
     *
     * Spring DAO-nun transient exception
     * hierarchy-sini simulyasiya edirik.
     *
     * attempt 1 -> error
     * attempt 2 -> error
     * attempt 3 -> success
     */

    static class TransientRetryProbeWriter
            implements ItemWriter<String> {

        private final AtomicInteger attempts =
                new AtomicInteger();

        private final List<String>
                successfullyWrittenItems =
                new ArrayList<>();

        @Override
        public void write(
                Chunk<? extends String> chunk
        ) {

            int currentAttempt =
                    attempts.incrementAndGet();

            /*
             * İlk iki invocation
             * temporary DB failure.
             */
            if (
                    currentAttempt <= 2
            ) {

                throw new TransientDataAccessResourceException(
                        "Simulated temporary database resource failure"
                );
            }

            /*
             * Üçüncü invocation uğurludur.
             */
            successfullyWrittenItems.addAll(
                    chunk.getItems()
            );
        }

        int getAttempts() {

            return attempts.get();
        }

        List<String>
        getSuccessfullyWrittenItems() {

            return List.copyOf(
                    successfullyWrittenItems
            );
        }

        void reset() {

            attempts.set(
                    0
            );

            successfullyWrittenItems.clear();
        }
    }

    /*
     * ============================================================
     * NON-RETRYABLE PROBE WRITER
     * ============================================================
     */

    static class NonRetryableProbeWriter
            implements ItemWriter<String> {

        private final AtomicInteger attempts =
                new AtomicInteger();

        @Override
        public void write(
                Chunk<? extends String> chunk
        ) {

            attempts.incrementAndGet();


            throw new CandidateExtractionException(
                    "Simulated deterministic candidate extraction failure"
            );
        }

        int getAttempts() {

            return attempts.get();
        }

        void reset() {

            attempts.set(
                    0
            );
        }
    }
}