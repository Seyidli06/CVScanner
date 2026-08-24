package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.upload.api.UploadStatusResponse;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class CvUploadQueryService {

    private final CvUploadRepository
            cvUploadRepository;

    public CvUploadQueryService(
            CvUploadRepository cvUploadRepository
    ) {

        this.cvUploadRepository =
                cvUploadRepository;
    }

    @Transactional(
            readOnly = true
    )
    public UploadStatusResponse getUploadStatus(
            UUID uploadId
    ) {

        Objects.requireNonNull(
                uploadId,
                "uploadId must not be null"
        );

        CvUpload upload =
                cvUploadRepository
                        .findById(
                                uploadId
                        )
                        .orElseThrow(
                                () ->
                                        new UploadNotFoundException(
                                                uploadId
                                        )
                        );

        return UploadStatusResponse.from(
                upload
        );
    }
}