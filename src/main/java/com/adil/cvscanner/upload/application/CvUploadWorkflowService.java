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

        



















        CvUpload upload =
                cvUploadService.upload(
                        file
                );

        





        try {

            processingJobLauncher.launch(
                    upload.getId()
            );

        } catch (
                CvProcessingLaunchException launchException
        ) {

            













            markFailedPreservingOriginalException(
                    upload,
                    launchException
            );

            



            throw launchException;
        }

        












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