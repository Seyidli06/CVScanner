package com.adil.cvscanner.processing.application;

public class InvalidProcessingFailureQueryException
        extends RuntimeException {

    public InvalidProcessingFailureQueryException(
            String message
    ) {

        super(
                message
        );
    }
}