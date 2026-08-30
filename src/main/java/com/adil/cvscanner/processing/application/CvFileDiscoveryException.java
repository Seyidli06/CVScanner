package com.adil.cvscanner.processing.application;

public class CvFileDiscoveryException extends RuntimeException {

    public CvFileDiscoveryException(String message) {
        super(message);
    }

    public CvFileDiscoveryException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
