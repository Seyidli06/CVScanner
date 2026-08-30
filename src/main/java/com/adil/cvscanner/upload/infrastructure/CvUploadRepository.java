package com.adil.cvscanner.upload.infrastructure;

import com.adil.cvscanner.upload.domain.CvUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CvUploadRepository
        extends JpaRepository<CvUpload, UUID> {
}
