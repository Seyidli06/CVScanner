package com.adil.cvscanner.processing.api;

import com.adil.cvscanner.processing.domain.ProcessingFailure;

import java.time.OffsetDateTime;

public record ProcessingFailureResponse(

        String filename,

        String errorCode,

        String errorMessage,

        OffsetDateTime createdAt

) {

    public static ProcessingFailureResponse from(
            ProcessingFailure failure
    ) {

        return new ProcessingFailureResponse(
                failure.getFilename(),
                failure.getErrorCode(),
                failure.getErrorMessage(),
                failure.getCreatedAt()
        );
    }
}
