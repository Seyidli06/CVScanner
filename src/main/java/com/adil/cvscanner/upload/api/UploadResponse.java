package com.adil.cvscanner.upload.api;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;

import java.util.UUID;

public record UploadResponse(
        UUID uploadId,
        String filename,
        UploadStatus status
) {

    public static UploadResponse from(CvUpload upload) {

        return new UploadResponse(
                upload.getId(),
                upload.getOriginalFilename(),
                upload.getStatus()
        );
    }
}
