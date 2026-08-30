package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.processing.application.CvProcessingJobLauncher;
import com.adil.cvscanner.processing.application.CvProcessingLaunchException;
import com.adil.cvscanner.upload.api.UploadResponse;
import com.adil.cvscanner.upload.domain.CvUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(
        MockitoExtension.class
)
class CvUploadWorkflowServiceTest {

    @Mock
    private CvUploadService cvUploadService;

    @Mock
    private CvProcessingJobLauncher
            processingJobLauncher;

    @Mock
    private CvUploadStatusService
            uploadStatusService;

    private CvUploadWorkflowService
            workflowService;

    @BeforeEach
    void setUp() {

        workflowService =
                new CvUploadWorkflowService(
                        cvUploadService,
                        processingJobLauncher,
                        uploadStatusService
                );
    }

    @Test
    void shouldUploadAndLaunchProcessingJob() {

        MockMultipartFile file =
                createMultipartFile();

        CvUpload upload =
                createUpload();

        UUID uploadId =
                upload.getId();

        when(
                cvUploadService.upload(
                        file
                )
        ).thenReturn(
                upload
        );

        UploadResponse response =
                workflowService
                        .uploadAndStartProcessing(
                                file
                        );

        verify(
                cvUploadService
        ).upload(
                file
        );

        verify(
                processingJobLauncher
        ).launch(
                uploadId
        );

        verify(
                uploadStatusService,
                never()
        ).markFailed(
                uploadId
        );

        assertThat(
                response.uploadId()
        ).isEqualTo(
                uploadId
        );

        assertThat(
                response.filename()
        ).isEqualTo(
                "candidates.zip"
        );
    }

    @Test
    void shouldMarkUploadFailedWhenBatchLaunchFails() {

        MockMultipartFile file =
                createMultipartFile();

        CvUpload upload =
                createUpload();

        UUID uploadId =
                upload.getId();

        when(
                cvUploadService.upload(
                        file
                )
        ).thenReturn(
                upload
        );

        CvProcessingLaunchException launchException =
                new CvProcessingLaunchException(
                        uploadId,
                        "Failed to launch CV processing job for upload: "
                                + uploadId,
                        new IllegalStateException(
                                "Simulated Batch launch failure"
                        )
                );

        when(
                processingJobLauncher.launch(
                        uploadId
                )
        ).thenThrow(
                launchException
        );

        assertThatThrownBy(
                () ->
                        workflowService
                                .uploadAndStartProcessing(
                                        file
                                )
        )

                .isSameAs(
                        launchException
                );

        verify(
                cvUploadService
        ).upload(
                file
        );

        verify(
                processingJobLauncher
        ).launch(
                uploadId
        );

        verify(
                uploadStatusService
        ).markFailed(
                uploadId
        );
    }

    @Test
    void shouldPreserveLaunchExceptionWhenFailedStatusUpdateAlsoFails() {

        MockMultipartFile file =
                createMultipartFile();

        CvUpload upload =
                createUpload();

        UUID uploadId =
                upload.getId();

        when(
                cvUploadService.upload(
                        file
                )
        ).thenReturn(
                upload
        );

        CvProcessingLaunchException launchException =
                new CvProcessingLaunchException(
                        uploadId,
                        "Failed to launch CV processing job for upload: "
                                + uploadId,
                        new IllegalStateException(
                                "Simulated Batch launch failure"
                        )
                );

        RuntimeException statusException =
                new RuntimeException(
                        "Simulated database failure while marking upload FAILED"
                );

        when(
                processingJobLauncher.launch(
                        uploadId
                )
        ).thenThrow(
                launchException
        );

        doThrow(
                statusException
        )
                .when(
                        uploadStatusService
                )
                .markFailed(
                        uploadId
                );

        assertThatThrownBy(
                () ->
                        workflowService
                                .uploadAndStartProcessing(
                                        file
                                )
        )
                .isSameAs(
                        launchException
                );

        assertThat(
                launchException
                        .getSuppressed()
        )
                .hasSize(
                        1
                )
                .containsExactly(
                        statusException
                );

        verify(
                uploadStatusService
        ).markFailed(
                uploadId
        );
    }

    private CvUpload createUpload() {

        CvUpload upload =
                new CvUpload(
                        "candidates.zip"
                );

        upload.registerDiscoveredFiles(
                2
        );

        return upload;
    }

    private MockMultipartFile createMultipartFile() {

        return new MockMultipartFile(
                "file",
                "candidates.zip",
                "application/zip",
                new byte[]{
                        1,
                        2,
                        3
                }
        );
    }
}
