package com.adil.cvscanner.common.exception;

import java.time.OffsetDateTime;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path
) {
}