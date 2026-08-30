package com.adil.cvscanner.common.api;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiErrorResponse(

        Instant timestamp,

        int status,

        String error,

        ApiErrorCode code,

        String message,

        String path

) {

    public static ApiErrorResponse of(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            String path
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path
        );
    }
}