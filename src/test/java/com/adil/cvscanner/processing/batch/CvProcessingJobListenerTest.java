package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.upload.application.CvUploadStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({
        MockitoExtension.class,
        OutputCaptureExtension.class
})
class CvProcessingJobListenerTest {

    @Mock
    private CvUploadStatusService
            cvUploadStatusService;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private JobParameters jobParameters;

    private CvProcessingJobListener listener;

    @BeforeEach
    void setUp() {

        listener =
                new CvProcessingJobListener(
                        cvUploadStatusService
                );

        when(
                jobExecution.getJobParameters()
        ).thenReturn(
                jobParameters
        );
    }

    





    @Test
    void shouldMarkProcessingAndLogSafeStartMetadata(
            CapturedOutput output
    ) {

        UUID uploadId =
                UUID.randomUUID();

        when(
                jobParameters.getString(
                        "uploadId"
                )
        ).thenReturn(
                uploadId.toString()
        );

        when(
                jobExecution.getId()
        ).thenReturn(
                101L
        );

        when(
                jobExecution.getStatus()
        ).thenReturn(
                BatchStatus.STARTING
        );

        listener.beforeJob(
                jobExecution
        );

        verify(
                cvUploadStatusService
        ).markProcessing(
                uploadId
        );

        assertThat(
                output.getOut()
        )
                .contains(
                        "CV_PROCESSING_JOB_STARTED"
                )
                .contains(
                        "uploadId="
                                + uploadId
                )
                .contains(
                        "jobExecutionId=101"
                )
                .contains(
                        "batchStatus=STARTING"
                );
    }

    





    @Test
    void shouldCompleteUploadAndLogSuccessfulJob(
            CapturedOutput output
    ) {

        UUID uploadId =
                UUID.randomUUID();

        when(
                jobParameters.getString(
                        "uploadId"
                )
        ).thenReturn(
                uploadId.toString()
        );

        when(
                jobExecution.getId()
        ).thenReturn(
                202L
        );

        when(
                jobExecution.getStatus()
        ).thenReturn(
                BatchStatus.COMPLETED
        );

        when(
                jobExecution.getExitStatus()
        ).thenReturn(
                ExitStatus.COMPLETED
        );

        listener.afterJob(
                jobExecution
        );

        verify(
                cvUploadStatusService
        ).complete(
                uploadId
        );

        verify(
                cvUploadStatusService,
                never()
        ).markFailed(
                uploadId
        );

        assertThat(
                output.getOut()
        )
                .contains(
                        "CV_PROCESSING_JOB_COMPLETED"
                )
                .contains(
                        "uploadId="
                                + uploadId
                )
                .contains(
                        "jobExecutionId=202"
                )
                .contains(
                        "batchStatus=COMPLETED"
                )
                .contains(
                        "exitCode=COMPLETED"
                );
    }

    













    @Test
    void shouldFailUploadWithoutLoggingExitDescription(
            CapturedOutput output
    ) {

        UUID uploadId =
                UUID.randomUUID();

        String sensitiveDescription =
                "candidateName=VERY_SECRET_PERSON "
                        + "email=secret@example.com "
                        + "CV_TEXT=VERY_SECRET_CV_TEXT";

        ExitStatus failureExitStatus =
                new ExitStatus(
                        "FAILED",
                        sensitiveDescription
                );

        when(
                jobParameters.getString(
                        "uploadId"
                )
        ).thenReturn(
                uploadId.toString()
        );

        when(
                jobExecution.getId()
        ).thenReturn(
                303L
        );

        when(
                jobExecution.getStatus()
        ).thenReturn(
                BatchStatus.FAILED
        );

        when(
                jobExecution.getExitStatus()
        ).thenReturn(
                failureExitStatus
        );

        listener.afterJob(
                jobExecution
        );

        verify(
                cvUploadStatusService
        ).markFailed(
                uploadId
        );

        verify(
                cvUploadStatusService,
                never()
        ).complete(
                uploadId
        );

        assertThat(
                output.getOut()
        )
                .contains(
                        "CV_PROCESSING_JOB_FAILED"
                )
                .contains(
                        "uploadId="
                                + uploadId
                )
                .contains(
                        "jobExecutionId=303"
                )
                .contains(
                        "batchStatus=FAILED"
                )
                .contains(
                        "exitCode=FAILED"
                )

                



                .doesNotContain(
                        "VERY_SECRET_PERSON"
                )
                .doesNotContain(
                        "secret@example.com"
                )
                .doesNotContain(
                        "VERY_SECRET_CV_TEXT"
                );
    }

    





    @Test
    void shouldRejectJobWithoutUploadId() {

        when(
                jobParameters.getString(
                        "uploadId"
                )
        ).thenReturn(
                null
        );

        org.assertj.core.api.Assertions
                .assertThatThrownBy(
                        () ->
                                listener.beforeJob(
                                        jobExecution
                                )
                )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Required batch parameter 'uploadId' is missing"
                );
    }

    





    @Test
    void shouldRejectInvalidUploadIdWithoutEchoingValue() {

        String maliciousValue =
                "SUPER_SECRET_INVALID_UPLOAD_ID";

        when(
                jobParameters.getString(
                        "uploadId"
                )
        ).thenReturn(
                maliciousValue
        );

        org.assertj.core.api.Assertions
                .assertThatThrownBy(
                        () ->
                                listener.beforeJob(
                                        jobExecution
                                )
                )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Required batch parameter 'uploadId' is invalid"
                )
                .hasMessageNotContaining(
                        maliciousValue
                );
    }
}