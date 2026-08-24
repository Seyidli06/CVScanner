package com.adil.cvscanner.upload.cleanup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UploadStorageCleanupRecordRepository
        extends JpaRepository<
        UploadStorageCleanupRecord,
        UUID
        > {
}