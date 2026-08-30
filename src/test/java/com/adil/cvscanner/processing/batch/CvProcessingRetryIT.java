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

    

















    @Test
    void shouldRetryTransientDataAccessFailureAndEventuallyComplete()
            throws Exception {

        transientRetryProbeWriter.reset();

        



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

        


        assertThat(
                stepExecution.getReadCount()
        ).isEqualTo(
                1
        );

        




        assertThat(
                stepExecution.getWriteCount()
        ).isEqualTo(
                1
        );

        










        assertThat(
                transientRetryProbeWriter
                        .getAttempts()
        ).isEqualTo(
                3
        );

        




        assertThat(
                transientRetryProbeWriter
                        .getSuccessfullyWrittenItems()
        ).containsExactly(
                "candidate-1"
        );
    }

    





















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

        






        assertThat(
                nonRetryableProbeWriter
                        .getAttempts()
        ).isEqualTo(
                1
        );
    }

    





    private JobParameters uniqueJobParameters() {

        





        return new JobParametersBuilder()
                .addString(
                        "testRunId",
                        UUID.randomUUID()
                                .toString()
                )
                .toJobParameters();
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
        );
    }

    





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

    











    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class RetryTestConfiguration {

        





        @Bean
        TransientRetryProbeWriter
        transientRetryProbeWriter() {

            return new TransientRetryProbeWriter();
        }

        





        @Bean
        NonRetryableProbeWriter
        nonRetryableProbeWriter() {

            return new NonRetryableProbeWriter();
        }

        





        @Bean("transientRetryTestStep")
        Step transientRetryTestStep(
                JobRepository jobRepository,
                PlatformTransactionManager
                        transactionManager,
                @Qualifier("cvProcessingRetryPolicy")
                RetryPolicy retryPolicy,
                TransientRetryProbeWriter writer
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

                    



                    .retryPolicy(
                            retryPolicy
                    )

                    .build();
        }

        





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

                    







                    .retryPolicy(
                            retryPolicy
                    )

                    .build();
        }

        





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

            



            if (
                    currentAttempt <= 2
            ) {

                throw new TransientDataAccessResourceException(
                        "Simulated temporary database resource failure"
                );
            }

            


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