package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.upload.application.CvUploadStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CvProcessingJobListener
        implements JobExecutionListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CvProcessingJobListener.class
            );

    private static final String UPLOAD_ID_PARAMETER =
            "uploadId";

    private final CvUploadStatusService
            cvUploadStatusService;

    public CvProcessingJobListener(
            CvUploadStatusService cvUploadStatusService
    ) {

        this.cvUploadStatusService =
                cvUploadStatusService;
    }

    /*
     * ============================================================
     * BEFORE JOB
     * ============================================================
     *
     * Business status:
     *
     * UPLOADED
     *      ↓
     * PROCESSING
     *
     *
     * Log:
     *
     * yalnız operational metadata.
     *
     * CV content, filename, candidate data və
     * request body burada yoxdur.
     */
    @Override
    public void beforeJob(
            JobExecution jobExecution
    ) {

        UUID uploadId =
                requireUploadId(
                        jobExecution
                );

        cvUploadStatusService.markProcessing(
                uploadId
        );

        log.info(
                "CV_PROCESSING_JOB_STARTED uploadId={} jobExecutionId={} batchStatus={}",
                uploadId,
                jobExecution.getId(),
                jobExecution.getStatus()
        );
    }

    /*
     * ============================================================
     * AFTER JOB
     * ============================================================
     */
    @Override
    public void afterJob(
            JobExecution jobExecution
    ) {

        UUID uploadId =
                requireUploadId(
                        jobExecution
                );

        BatchStatus batchStatus =
                jobExecution.getStatus();

        String exitCode =
                resolveSafeExitCode(
                        jobExecution
                );

        /*
         * ========================================================
         * SUCCESS
         * ========================================================
         *
         * Business status-u CvUpload domain özü müəyyən edir:
         *
         * failedFiles == 0
         *      → COMPLETED
         *
         * failedFiles > 0
         *      → COMPLETED_WITH_ERRORS
         *
         * Biz burada counter-ları yenidən hesablamırıq.
         */
        if (
                batchStatus
                        == BatchStatus.COMPLETED
        ) {

            cvUploadStatusService.complete(
                    uploadId
            );

            log.info(
                    "CV_PROCESSING_JOB_COMPLETED uploadId={} jobExecutionId={} batchStatus={} exitCode={}",
                    uploadId,
                    jobExecution.getId(),
                    batchStatus,
                    exitCode
            );

            return;
        }

        /*
         * ========================================================
         * FAILURE
         * ========================================================
         *
         * FAILED
         * STOPPED
         * ABANDONED
         * və s.
         *
         * normal successful terminal state deyil.
         */
        cvUploadStatusService.markFailed(
                uploadId
        );

        /*
         * QƏSDƏN bunları etmirik:
         *
         * jobExecution.getExitStatus()
         *             .getExitDescription()
         *
         * jobExecution.getAllFailureExceptions()
         *
         * exception.getMessage()
         *
         * Çünki həmin string-lərdə filename,
         * parser detail-i və gələcəkdə hətta
         * document-derived data ola bilər.
         */
        log.warn(
                "CV_PROCESSING_JOB_FAILED uploadId={} jobExecutionId={} batchStatus={} exitCode={}",
                uploadId,
                jobExecution.getId(),
                batchStatus,
                exitCode
        );
    }

    /*
     * ============================================================
     * SAFE UPLOAD ID EXTRACTION
     * ============================================================
     */
    private UUID requireUploadId(
            JobExecution jobExecution
    ) {

        String rawUploadId =
                jobExecution
                        .getJobParameters()
                        .getString(
                                UPLOAD_ID_PARAMETER
                        );

        if (
                rawUploadId == null
                        || rawUploadId.isBlank()
        ) {

            throw new IllegalStateException(
                    "Required batch parameter 'uploadId' is missing"
            );
        }

        try {

            return UUID.fromString(
                    rawUploadId
            );

        } catch (
                IllegalArgumentException exception
        ) {

            /*
             * rawUploadId-ni exception message-ə
             * daxil etmirik.
             */
            throw new IllegalStateException(
                    "Required batch parameter 'uploadId' is invalid"
            );
        }
    }

    /*
     * ============================================================
     * SAFE EXIT STATUS
     * ============================================================
     *
     * ExitStatus iki əsas məlumat daşıya bilər:
     *
     * exitCode
     * exitDescription
     *
     * Biz yalnız exitCode istifadə edirik.
     *
     * exitDescription bəzən exception məlumatı,
     * filename və başqa internal detail daşıya bilər.
     */
    private String resolveSafeExitCode(
            JobExecution jobExecution
    ) {

        ExitStatus exitStatus =
                jobExecution.getExitStatus();

        if (
                exitStatus == null
                        ||
                        exitStatus.getExitCode() == null
                        ||
                        exitStatus
                                .getExitCode()
                                .isBlank()
        ) {

            return "-";
        }

        return exitStatus.getExitCode();
    }
}