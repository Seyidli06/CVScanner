package com.adil.cvscanner.upload.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.upload")
public record UploadStorageProperties(

        @NotNull
        Path storageRoot,

        @Min(1)
        int maxEntries,

        @NotNull
        DataSize maxExtractedSize,

        @NotNull
        DataSize maxSingleFileSize

) {
}