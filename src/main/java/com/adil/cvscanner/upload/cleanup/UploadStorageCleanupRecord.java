package com.adil.cvscanner.upload.cleanup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "upload_storage_cleanup"
)
public class UploadStorageCleanupRecord {

    @Id
    @Column(
            name = "upload_id",
            nullable = false,
            updatable = false
    )
    private UUID uploadId;

    @Column(
            name = "cleaned_at",
            nullable = false
    )
    private OffsetDateTime cleanedAt;

    protected UploadStorageCleanupRecord() {
    }

    public UploadStorageCleanupRecord(
            UUID uploadId,
            OffsetDateTime cleanedAt
    ) {

        this.uploadId =
                Objects.requireNonNull(
                        uploadId,
                        "uploadId must not be null"
                );

        this.cleanedAt =
                Objects.requireNonNull(
                        cleanedAt,
                        "cleanedAt must not be null"
                );
    }

    public UUID getUploadId() {

        return uploadId;
    }

    public OffsetDateTime getCleanedAt() {

        return cleanedAt;
    }
}
