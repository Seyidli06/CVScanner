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

        











        cvUploadStatusService.markFailed(
                uploadId
        );

        













        log.warn(
                "CV_PROCESSING_JOB_FAILED uploadId={} jobExecutionId={} batchStatus={} exitCode={}",
                uploadId,
                jobExecution.getId(),
                batchStatus,
                exitCode
        );
    }

    




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

            



            throw new IllegalStateException(
                    "Required batch parameter 'uploadId' is invalid"
            );
        }
    }

    














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