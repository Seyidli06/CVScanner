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

    /*
     * ============================================================
     * TEST 1
     * SUCCESSFUL LAUNCH
     * ============================================================
     */

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

        /*
         * launch() void deyil,
         * JobExecution qaytarır.
         *
         * Amma workflow həmin return value-dan
         * istifadə etmir.
         *
         * Mockito default olaraq null qaytara
         * bildiyi üçün ayrıca stub lazım deyil.
         */

        UploadResponse response =
                workflowService
                        .uploadAndStartProcessing(
                                file
                        );

        /*
         * Upload servisi çağırılıb.
         */
        verify(
                cvUploadService
        ).upload(
                file
        );

        /*
         * Həmin CvUpload-un ID-si ilə
         * Batch job start olunub.
         */
        verify(
                processingJobLauncher
        ).launch(
                uploadId
        );

        /*
         * Launch successful olduğuna görə
         * workflow özü FAILED etməməlidir.
         *
         * Status lifecycle artıq
         * JobExecutionListener-in işidir.
         */
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

    /*
     * ============================================================
     * TEST 2
     * IMMEDIATE LAUNCH FAILURE
     * ============================================================
     */

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
                /*
                 * Eyni original exception
                 * yuxarı ötürülməlidir.
                 */
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

        /*
         * Əsas Phase 7.5 assertion.
         */
        verify(
                uploadStatusService
        ).markFailed(
                uploadId
        );
    }

    /*
     * ============================================================
     * TEST 3
     * STATUS UPDATE ITSELF ALSO FAILS
     * ============================================================
     *
     * Launch failure əsas/root exception olaraq
     * qorunmalıdır.
     */

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

        /*
         * Root exception dəyişməyib.
         */
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

    /*
     * ============================================================
     * TEST DATA
     * ============================================================
     */

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