package com.adil.cvscanner.processing.application;

import com.adil.cvscanner.processing.api.ProcessingFailurePageResponse;
import com.adil.cvscanner.processing.domain.ProcessingFailure;
import com.adil.cvscanner.processing.infrastructure.ProcessingFailureRepository;
import com.adil.cvscanner.upload.application.UploadNotFoundException;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class ProcessingFailureQueryService {

    private static final int MAX_PAGE_SIZE =
            100;

    private final ProcessingFailureRepository
            processingFailureRepository;

    private final CvUploadRepository
            cvUploadRepository;

    public ProcessingFailureQueryService(
            ProcessingFailureRepository
                    processingFailureRepository,
            CvUploadRepository cvUploadRepository
    ) {

        this.processingFailureRepository =
                processingFailureRepository;

        this.cvUploadRepository =
                cvUploadRepository;
    }

    /*
     * ============================================================
     * SEARCH FAILURES
     * ============================================================
     */

    @Transactional(
            readOnly = true
    )
    public ProcessingFailurePageResponse findByUpload(
            UUID uploadId,
            int page,
            int size
    ) {

        Objects.requireNonNull(
                uploadId,
                "uploadId must not be null"
        );

        validatePagination(
                page,
                size
        );

        /*
         * =====================================================
         * UPLOAD EXISTENCE
         * =====================================================
         *
         * Bunları ayırmaq istəyirik:
         *
         * upload mövcuddur,
         * amma failure yoxdur
         *
         *              VS
         *
         * upload ümumiyyətlə yoxdur
         */

        if (
                !cvUploadRepository.existsById(
                        uploadId
                )
        ) {

            throw new UploadNotFoundException(
                    uploadId
            );
        }

        /*
         * =====================================================
         * SORT
         * =====================================================
         *
         * Ən yeni failure yuxarıda.
         *
         * createdAt eyni olsa filename
         * secondary deterministic sort-dur.
         */

        Sort sort =
                Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                        .and(
                                Sort.by(
                                        Sort.Direction.ASC,
                                        "filename"
                                )
                        );

        PageRequest pageable =
                PageRequest.of(
                        page,
                        size,
                        sort
                );

        Page<ProcessingFailure> failures =
                processingFailureRepository
                        .findAllByUpload_Id(
                                uploadId,
                                pageable
                        );

        return ProcessingFailurePageResponse.from(
                failures
        );
    }

    /*
     * ============================================================
     * PAGINATION VALIDATION
     * ============================================================
     */

    private void validatePagination(
            int page,
            int size
    ) {

        if (
                page < 0
        ) {

            throw new InvalidProcessingFailureQueryException(
                    "page must not be negative"
            );
        }

        if (
                size < 1
        ) {

            throw new InvalidProcessingFailureQueryException(
                    "size must be at least 1"
            );
        }

        if (
                size > MAX_PAGE_SIZE
        ) {

            throw new InvalidProcessingFailureQueryException(
                    "size must not exceed "
                            + MAX_PAGE_SIZE
            );
        }
    }
}