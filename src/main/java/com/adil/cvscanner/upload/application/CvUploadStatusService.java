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

    /*
     * ============================================================
     * PROCESSING START
     * ============================================================
     */

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

    /*
     * ============================================================
     * LIVE SUCCESS COUNTER
     * ============================================================
     *
     * Chunk uğurla writer-dan keçəndə
     * həmin chunk-dakı item sayı qədər:
     *
     * processedFiles
     *
     * artırılır.
     *
     * Bu metod chunk transaction-a
     * qoşulur.
     */

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

    /*
     * ============================================================
     * LIVE FAILURE COUNTER
     * ============================================================
     *
     * CV həqiqətən skip olunanda:
     *
     * failedFiles++
     */

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

    /*
     * ============================================================
     * SUCCESSFUL TERMINAL STATE
     * ============================================================
     *
     * Counter-lar artıq chunk-by-chunk
     * DB-də saxlanıldığı üçün burada
     * yenidən Batch metadata-dan
     * overwrite etmirik.
     */

    @Transactional
    public void complete(
            UUID uploadId
    ) {

        CvUpload upload =
                findUpload(
                        uploadId
                );

        /*
         * CvUpload.complete():
         *
         * failedFiles == 0
         *      -> COMPLETED
         *
         * failedFiles > 0
         *      -> COMPLETED_WITH_ERRORS
         */
        upload.complete();

        cvUploadRepository.saveAndFlush(
                upload
        );
    }

    /*
     * ============================================================
     * FAILED TERMINAL STATE
     * ============================================================
     */

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