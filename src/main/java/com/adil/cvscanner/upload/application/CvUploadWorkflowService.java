package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.processing.application.CvProcessingJobLauncher;
import com.adil.cvscanner.processing.application.CvProcessingLaunchException;
import com.adil.cvscanner.upload.api.UploadResponse;
import com.adil.cvscanner.upload.domain.CvUpload;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CvUploadWorkflowService {

    private final CvUploadService cvUploadService;

    private final CvProcessingJobLauncher processingJobLauncher;

    private final CvUploadStatusService uploadStatusService;

    public CvUploadWorkflowService(
            CvUploadService cvUploadService,
            CvProcessingJobLauncher processingJobLauncher,
            CvUploadStatusService uploadStatusService
    ) {
        this.cvUploadService =
                cvUploadService;

        this.processingJobLauncher =
                processingJobLauncher;

        this.uploadStatusService =
                uploadStatusService;
    }

    public UploadResponse uploadAndStartProcessing(
            MultipartFile file
    ) {

        /*
         * =====================================================
         * 1. SECURE UPLOAD + EXTRACTION
         * =====================================================
         *
         * Bu metod:
         *
         * - staging
         * - ZIP validation
         * - extraction
         * - CvUpload persist
         *
         * işlərini tamamlayır.
         *
         * Buradan qayıdanda:
         *
         * DB row var.
         * storage hazırdır.
         * status = UPLOADED.
         */
        CvUpload upload =
                cvUploadService.upload(
                        file
                );

        /*
         * =====================================================
         * 2. START BATCH JOB
         * =====================================================
         */

        try {

            processingJobLauncher.launch(
                    upload.getId()
            );

        } catch (
                CvProcessingLaunchException launchException
        ) {

            /*
             * =================================================
             * IMMEDIATE LAUNCH FAILURE
             * =================================================
             *
             * Job heç normal processing lifecycle-a
             * daxil ola bilmədi.
             *
             * CvProcessingJobListener.beforeJob()
             * çağırılmaya da bilər.
             *
             * Ona görə upload-u burada FAILED
             * edirik.
             */
            markFailedPreservingOriginalException(
                    upload,
                    launchException
            );

            /*
             * Controller/global exception handling
             * original launch exception-u görməlidir.
             */
            throw launchException;
        }

        /*
         * Job qəbul edildi.
         *
         * Bundan sonrakı lifecycle:
         *
         * beforeJob:
         * UPLOADED -> PROCESSING
         *
         * afterJob:
         * -> COMPLETED
         * -> COMPLETED_WITH_ERRORS
         * -> FAILED
         */
        return UploadResponse.from(
                upload
        );
    }

    private void markFailedPreservingOriginalException(
            CvUpload upload,
            CvProcessingLaunchException launchException
    ) {

        try {

            uploadStatusService.markFailed(
                    upload.getId()
            );

        } catch (
                RuntimeException statusUpdateException
        ) {

            launchException.addSuppressed(
                    statusUpdateException
            );
        }
    }
}