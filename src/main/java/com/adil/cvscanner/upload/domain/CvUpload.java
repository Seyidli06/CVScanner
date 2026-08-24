package com.adil.cvscanner.upload.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_upload")
public class CvUpload implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UploadStatus status;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "processed_files", nullable = false)
    private int processedFiles;

    @Column(name = "failed_files", nullable = false)
    private int failedFiles;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Transient
    private boolean isNew = true;

    protected CvUpload() {
    }

    public CvUpload(String originalFilename) {
        this.id = UUID.randomUUID();
        this.originalFilename = originalFilename;
        this.status = UploadStatus.UPLOADED;
        this.createdAt = OffsetDateTime.now();
        this.totalFiles = 0;
        this.processedFiles = 0;
        this.failedFiles = 0;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    private void markNotNew() {
        this.isNew = false;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getProcessedFiles() {
        return processedFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void registerDiscoveredFiles(int totalFiles) {
        if (totalFiles <= 0) {
            throw new IllegalArgumentException(
                    "Upload must contain at least one CV"
            );
        }

        this.totalFiles = totalFiles;
    }

    public void markProcessing() {
        this.status = UploadStatus.PROCESSING;
    }

    public void incrementProcessed() {
        this.processedFiles++;
    }

    public void incrementFailed() {
        this.failedFiles++;
    }

    public void complete() {
        this.status = failedFiles == 0
                ? UploadStatus.COMPLETED
                : UploadStatus.COMPLETED_WITH_ERRORS;

        this.completedAt = OffsetDateTime.now();
    }

    public void fail() {
        this.status = UploadStatus.FAILED;
        this.completedAt = OffsetDateTime.now();
    }

    public void synchronizeProcessingResult(
            int processedFiles,
            int failedFiles
    ) {

        if (processedFiles < 0) {
            throw new IllegalArgumentException(
                    "processedFiles must not be negative"
            );
        }

        if (failedFiles < 0) {
            throw new IllegalArgumentException(
                    "failedFiles must not be negative"
            );
        }

        if (
                processedFiles + failedFiles
                        > totalFiles
        ) {

            throw new IllegalArgumentException(
                    "processedFiles + failedFiles cannot exceed totalFiles"
            );
        }

        this.processedFiles =
                processedFiles;

        this.failedFiles =
                failedFiles;
    }
}