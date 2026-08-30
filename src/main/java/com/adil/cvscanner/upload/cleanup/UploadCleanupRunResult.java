package com.adil.cvscanner.upload.cleanup;

public record UploadCleanupRunResult(
        int selected,
        int deleted,
        int alreadyAbsent,
        int failed
) {

    public int completed() {

        return deleted
                + alreadyAbsent;
    }
}
