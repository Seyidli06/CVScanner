package com.adil.cvscanner.upload.application;

public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String message) {
        super(message);
    }
}
