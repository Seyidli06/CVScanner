package com.adil.cvscanner.upload.application;

import java.nio.file.Path;

public record ZipExtractionResult(
        int fileCount,
        long extractedBytes,
        Path extractionDirectory
) {
}