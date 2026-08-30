package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CvUploadStatusService {

    private final CvUploadRepository cvUploadRepository;

    public CvUploadStatusService(
            CvUploadRepository cvUploadRepository
    ) {
        this.cvUploadRepository =
                cvUploadRepository;
    }

    @Transactional
    public void markProcessing(
            UUID uploadId
    ) {

        CvUpload upload =
                findUpload(
                        uploadId
                );

        upload.markProcessing();

        cvUploadRepository.saveAndFlush(
                upload
        );
    }

    @Transactional
    public void recordProcessed(
            UUID uploadId,
            int count
    ) {

        if (count <= 0) {
            return;
        }

        CvUpload upload =
                findUpload(
                        uploadId
                );

        for (
                int index = 0;
                index < count;
                index++
        ) {

            upload.incrementProcessed();
        }

        cvUploadRepository.saveAndFlush(
                upload
        );
    }

    @Transactional
    public void recordFailed(
            UUID uploadId
    ) {

        CvUpload upload =
                findUpload(
                        uploadId
                );

        upload.incrementFailed();

        cvUploadRepository.saveAndFlush(
                upload
        );
    }

    @Transactional
    public void complete(
            UUID uploadId
    ) {

        CvUpload upload =
                findUpload(
                        uploadId
                );

        upload.complete();

        cvUploadRepository.saveAndFlush(
                upload
        );
    }

    @Transactional
    public void markFailed(
            UUID uploadId
    ) {

        CvUpload upload =
                findUpload(
                        uploadId
                );

        upload.fail();

        cvUploadRepository.saveAndFlush(
                upload
        );
    }

    private CvUpload findUpload(
            UUID uploadId
    ) {

        return cvUploadRepository
                .findById(
                        uploadId
                )
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "CvUpload not found: "
                                                + uploadId
                                )
                );
    }
}
