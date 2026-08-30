package com.adil.cvscanner.upload.cleanup;

public record UploadCleanupExecutionResult(
        boolean executed,
        UploadCleanupRunResult cleanupResult
) {

    public static UploadCleanupExecutionResult skipped() {

        return new UploadCleanupExecutionResult(
                false,
                null
        );
    }

    public static UploadCleanupExecutionResult executed(
            UploadCleanupRunResult result
    ) {

        if (
                result == null
        ) {

            throw new IllegalArgumentException(
                    "cleanup result must not be null"
            );
        }

        return new UploadCleanupExecutionResult(
                true,
                result
        );
    }
}
