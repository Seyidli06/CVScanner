package com.adil.cvscanner.upload.cleanup;

public class UploadCleanupLockException
        extends RuntimeException {

    public UploadCleanupLockException(
            String message,
            Throwable cause
    ) {

        super(
                message,
                cause
        );
    }

    public UploadCleanupLockException(
            String message
    ) {

        super(
                message
        );
    }
}