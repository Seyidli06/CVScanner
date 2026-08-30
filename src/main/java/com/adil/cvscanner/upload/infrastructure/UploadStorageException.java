package com.adil.cvscanner.upload.infrastructure;

public class UploadStorageException extends RuntimeException {

    public UploadStorageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
