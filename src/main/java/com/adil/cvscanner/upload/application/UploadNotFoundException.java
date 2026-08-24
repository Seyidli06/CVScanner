package com.adil.cvscanner.upload.application;

import java.util.UUID;

public class UploadNotFoundException
        extends RuntimeException {

    private final UUID uploadId;

    public UploadNotFoundException(
            UUID uploadId
    ) {

        super(
                "CV upload not found: "
                        + uploadId
        );

        this.uploadId =
                uploadId;
    }

    public UUID getUploadId() {
        return uploadId;
    }
}