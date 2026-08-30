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

    









































    @Test
    void shouldRestartFailedJobFromLastCommittedCheckpoint()
            throws Exception {

        





        JobExecution launchedExecution =
                jobOperator.start(
                        restartTestJob,
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

        StepExecution failedStepExecution =
                findStepExecution(
                        failedExecution
                );

        assertThat(
                failedStepExecution.getStatus()
        ).isEqualTo(
                BatchStatus.FAILED
        );

        










        assertThat(
                restartProbeWriter
                        .getSuccessfullyWrittenFiles()
        ).containsExactly(
                "01.pdf",
                "02.pdf"
        );

        





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

        














        JobExecution restartedExecution =
                jobOperator.restart(
                        failedExecution
                );

        


        assertThat(
                restartedExecution.getId()
        ).isNotEqualTo(
                failedExecution.getId()
        );

        









        assertThat(
                restartedExecution
                        .getJobInstance()
                        .getId()
        ).isEqualTo(
                failedExecution
                        .getJobInstance()
                        .getId()
        );

        



        assertThat(
                restartedExecution
                        .getJobParameters()
        ).isEqualTo(
                failedExecution
                        .getJobParameters()
        );

        





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

    





    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class RestartTestConfiguration {

        











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

        










        @Bean("cvProcessingRestartTestProcessor")
        ItemProcessor<Path, Path>
        cvProcessingRestartTestProcessor() {

            return path -> path;
        }

        





        @Bean
        RestartProbeWriter restartProbeWriter() {

            return new RestartProbeWriter();
        }

        





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

                    







                    .build();
        }

        





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

    















    static class RestartProbeWriter
            implements ItemWriter<Path> {

        


        private final AtomicBoolean failOnce =
                new AtomicBoolean(
                        true
                );

        




        private final List<List<String>>
                invokedChunks =
                new ArrayList<>();

        



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

            



            invokedChunks.add(
                    List.copyOf(
                            filenames
                    )
            );

            




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