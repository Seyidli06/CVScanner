package com.adil.cvscanner.processing.infrastructure;

import com.adil.cvscanner.processing.domain.ProcessingFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessingFailureRepository
        extends JpaRepository<ProcessingFailure, UUID> {

    Page<ProcessingFailure> findAllByUpload_Id(
            UUID uploadId,
            Pageable pageable
    );
}