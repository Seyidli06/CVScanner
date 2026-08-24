package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.application.CandidateExtractionException;
import com.adil.cvscanner.candidate.application.CandidateExtractor;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.processing.application.DocumentParsingException;
import com.adil.cvscanner.processing.application.DocumentTextExtractor;
import com.adil.cvscanner.processing.application.ParsedDocument;
import com.adil.cvscanner.processing.infrastructure.CvFileDiscoveryService;
import com.adil.cvscanner.processing.infrastructure.ProcessingFailureRepository;
import com.adil.cvscanner.upload.application.CvUploadStatusService;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.util.UUID;

@Configuration
public class CvProcessingJobConfig {

    private static final int CHUNK_SIZE =
            10;

    private static final long SKIP_LIMIT =
            50;

    /*
     * ============================================================
     * READER
     * ============================================================
     */

    @Bean
    @StepScope
    public CvFileItemReader cvFileReader(
            @Value("#{jobParameters['uploadId']}")
            String uploadId,
            CvFileDiscoveryService discoveryService
    ) {

        UUID parsedUploadId =
                parseUploadId(
                        uploadId
                );

        return new CvFileItemReader(
                discoveryService.findCvFiles(
                        parsedUploadId
                )
        );
    }

    /*
     * ============================================================
     * PROCESSOR
     * ============================================================
     */

    @Bean
    public ItemProcessor<Path, CandidateDraft>
    cvCandidateProcessor(
            DocumentTextExtractor documentTextExtractor,
            CandidateExtractor candidateExtractor
    ) {

        return path -> {

            ParsedDocument parsedDocument =
                    documentTextExtractor.extract(
                            path
                    );

            return candidateExtractor.extract(
                    parsedDocument
            );
        };
    }

    /*
     * ============================================================
     * WRITER
     * ============================================================
     */

    @Bean
    @StepScope
    public CandidateItemWriter candidateWriter(
            @Value("#{jobParameters['uploadId']}")
            String uploadId,
            CvUploadRepository cvUploadRepository,
            CandidateRepository candidateRepository
    ) {

        UUID parsedUploadId =
                parseUploadId(
                        uploadId
                );

        return new CandidateItemWriter(
                parsedUploadId,
                cvUploadRepository,
                candidateRepository
        );
    }

    /*
     * ============================================================
     * LIVE PROGRESS LISTENER
     * ============================================================
     */

    @Bean
    @StepScope
    public CvProcessingProgressListener
    cvProcessingProgressListener(
            @Value("#{jobParameters['uploadId']}")
            String uploadId,
            CvUploadStatusService uploadStatusService
    ) {

        UUID parsedUploadId =
                parseUploadId(
                        uploadId
                );

        return new CvProcessingProgressListener(
                parsedUploadId,
                uploadStatusService
        );
    }

    /*
     * ============================================================
     * SKIP LISTENER
     * ============================================================
     */

    @Bean
    @StepScope
    public CvProcessingSkipListener
    cvProcessingSkipListener(
            @Value("#{jobParameters['uploadId']}")
            String uploadId,
            CvUploadRepository cvUploadRepository,
            ProcessingFailureRepository
                    processingFailureRepository,
            CvUploadStatusService uploadStatusService
    ) {

        UUID parsedUploadId =
                parseUploadId(
                        uploadId
                );

        return new CvProcessingSkipListener(
                parsedUploadId,
                cvUploadRepository,
                processingFailureRepository,
                uploadStatusService
        );
    }

    /*
     * ============================================================
     * STEP
     * ============================================================
     */

    @Bean
    public Step processCvFilesStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CvFileItemReader cvFileReader,
            ItemProcessor<Path, CandidateDraft>
                    cvCandidateProcessor,
            CandidateItemWriter candidateWriter,
            CvProcessingProgressListener
                    cvProcessingProgressListener,
            CvProcessingSkipListener
                    cvProcessingSkipListener,
            @Qualifier("cvProcessingRetryPolicy")
            RetryPolicy cvProcessingRetryPolicy
    ) {

        return new ChunkOrientedStepBuilder
                <Path, CandidateDraft>(
                "processCvFilesStep",
                jobRepository,
                CHUNK_SIZE
        )

                /*
                 * =================================================
                 * TRANSACTION
                 * =================================================
                 */

                .transactionManager(
                        transactionManager
                )

                /*
                 * =================================================
                 * PIPELINE
                 * =================================================
                 */

                .reader(
                        cvFileReader
                )

                .processor(
                        cvCandidateProcessor
                )

                .writer(
                        candidateWriter
                )

                /*
                 * =================================================
                 * LIVE PROGRESS
                 * =================================================
                 *
                 * after successful chunk:
                 *
                 * processedFiles += chunk.size()
                 */
                .listener(
                        cvProcessingProgressListener
                )

                /*
                 * =================================================
                 * FAULT TOLERANCE
                 * =================================================
                 */

                .faultTolerant()

                /*
                 * Deterministic document/business
                 * problemləri retry edilmir,
                 * skip olunur.
                 */
                .skip(
                        DocumentParsingException.class,
                        CandidateExtractionException.class
                )

                .skipLimit(
                        SKIP_LIMIT
                )

                /*
                 * Real skip:
                 *
                 * processing_failure
                 * +
                 * failedFiles++
                 */
                .skipListener(
                        cvProcessingSkipListener
                )

                /*
                 * Yalnız transient DB
                 * exception-ları retry.
                 */
                .retryPolicy(
                        cvProcessingRetryPolicy
                )

                .build();
    }

    /*
     * ============================================================
     * JOB
     * ============================================================
     */

    @Bean
    public Job cvProcessingJob(
            JobRepository jobRepository,
            Step processCvFilesStep,
            CvProcessingJobListener
                    cvProcessingJobListener
    ) {

        return new JobBuilder(
                "cvProcessingJob",
                jobRepository
        )
                .listener(
                        cvProcessingJobListener
                )
                .start(
                        processCvFilesStep
                )
                .build();
    }

    /*
     * ============================================================
     * COMMON
     * ============================================================
     */

    private UUID parseUploadId(
            String uploadId
    ) {

        if (
                uploadId == null
                        || uploadId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Missing uploadId job parameter"
            );
        }

        try {

            return UUID.fromString(
                    uploadId
            );

        } catch (
                IllegalArgumentException exception
        ) {

            throw new IllegalArgumentException(
                    "Invalid uploadId job parameter: "
                            + uploadId,
                    exception
            );
        }
    }
}