package com.adil.cvscanner.processing.application;

import java.util.UUID;

public class CvProcessingLaunchException
        extends RuntimeException {

    private final UUID uploadId;

    public CvProcessingLaunchException(
            UUID uploadId,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.uploadId = uploadId;
    }

    public UUID getUploadId() {
        return uploadId;
    }
}
