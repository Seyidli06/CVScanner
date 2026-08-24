package com.adil.cvscanner.upload.api;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UploadStatusResponse(

        UUID uploadId,

        String filename,

        UploadStatus status,

        int totalFiles,

        int processedFiles,

        int failedFiles,

        OffsetDateTime createdAt,

        OffsetDateTime completedAt

) {

    public static UploadStatusResponse from(
            CvUpload upload
    ) {

        return new UploadStatusResponse(
                upload.getId(),
                upload.getOriginalFilename(),
                upload.getStatus(),
                upload.getTotalFiles(),
                upload.getProcessedFiles(),
                upload.getFailedFiles(),
                upload.getCreatedAt(),
                upload.getCompletedAt()
        );
    }
}