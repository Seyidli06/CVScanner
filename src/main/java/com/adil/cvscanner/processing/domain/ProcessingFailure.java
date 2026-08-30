package com.adil.cvscanner.processing.domain;

import com.adil.cvscanner.upload.domain.CvUpload;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processing_failure")
public class ProcessingFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false)
    private CvUpload upload;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ProcessingFailure() {
    }

    public ProcessingFailure(
            CvUpload upload,
            String filename,
            String errorCode,
            String errorMessage
    ) {
        this.upload = upload;
        this.filename = filename;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public CvUpload getUpload() {
        return upload;
    }

    public String getFilename() {
        return filename;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
