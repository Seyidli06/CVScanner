package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.application.CandidateExtractionException;
import com.adil.cvscanner.processing.application.DocumentParsingException;
import com.adil.cvscanner.processing.domain.ProcessingFailure;
import com.adil.cvscanner.processing.infrastructure.ProcessingFailureRepository;
import com.adil.cvscanner.upload.application.CvUploadStatusService;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.springframework.batch.core.listener.SkipListener;

import java.nio.file.Path;
import java.util.UUID;

public class CvProcessingSkipListener
        implements SkipListener<Path, CandidateDraft> {

    private static final String CANDIDATE_EXTRACTION_FAILED =
            "CANDIDATE_EXTRACTION_FAILED";

    private static final String PROCESSING_FAILED =
            "PROCESSING_FAILED";

    private static final int MAX_ERROR_MESSAGE_LENGTH =
            2000;

    private final UUID uploadId;

    private final CvUploadRepository cvUploadRepository;

    private final ProcessingFailureRepository
            processingFailureRepository;

    private final CvUploadStatusService
            uploadStatusService;

    public CvProcessingSkipListener(
            UUID uploadId,
            CvUploadRepository cvUploadRepository,
            ProcessingFailureRepository
                    processingFailureRepository,
            CvUploadStatusService uploadStatusService
    ) {

        this.uploadId =
                uploadId;

        this.cvUploadRepository =
                cvUploadRepository;

        this.processingFailureRepository =
                processingFailureRepository;

        this.uploadStatusService =
                uploadStatusService;
    }

    /*
     * ============================================================
     * READ SKIP
     * ============================================================
     *
     * Hazırkı CvFileItemReader əvvəlcədən
     * hazırlanmış Path list-i oxuyur.
     *
     * Skippable exception-larımız da
     * processor exception-larıdır.
     *
     * Buna görə hazırda real read skip
     * gözləmirik.
     */

    @Override
    public void onSkipInRead(
            Throwable throwable
    ) {
        // intentionally empty
    }

    /*
     * ============================================================
     * PROCESS SKIP
     * ============================================================
     *
     * Əsas fault-tolerance flow buradadır:
     *
     * broken.pdf
     *      ↓
     * DocumentParsingException
     *      ↓
     * SKIP
     *      ↓
     * processing_failure INSERT
     *      +
     * failedFiles++
     */

    @Override
    public void onSkipInProcess(
            Path item,
            Throwable throwable
    ) {

        CvUpload upload =
                findUpload();

        String filename =
                extractFilename(
                        item
                );

        String errorCode =
                resolveErrorCode(
                        throwable
                );

        String errorMessage =
                sanitizeErrorMessage(
                        throwable
                );

        ProcessingFailure failure =
                new ProcessingFailure(
                        upload,
                        filename,
                        errorCode,
                        errorMessage
                );

        /*
         * Audit row.
         */
        processingFailureRepository
                .saveAndFlush(
                        failure
                );

        /*
         * Live failed counter.
         *
         * SkipListener callback real skip
         * üçün commit-dən əvvəl çağırılır.
         */
        uploadStatusService
                .recordFailed(
                        uploadId
                );
    }

    /*
     * ============================================================
     * WRITE SKIP
     * ============================================================
     *
     * Hazırkı writer DB/infrastructure
     * exception-ları skippable deyil.
     *
     * Ona görə writer problemi:
     *
     * STEP FAILED
     *
     * olmalıdır.
     */

    @Override
    public void onSkipInWrite(
            CandidateDraft item,
            Throwable throwable
    ) {
        // intentionally empty
    }

    /*
     * ============================================================
     * UPLOAD
     * ============================================================
     */

    private CvUpload findUpload() {

        return cvUploadRepository
                .findById(
                        uploadId
                )
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "CvUpload not found for processing failure audit: "
                                                + uploadId
                                )
                );
    }

    /*
     * ============================================================
     * FILENAME
     * ============================================================
     */

    private String extractFilename(
            Path item
    ) {

        if (
                item == null
                        || item.getFileName() == null
        ) {

            return "unknown";
        }

        return item
                .getFileName()
                .toString();
    }

    /*
     * ============================================================
     * ERROR CODE
     * ============================================================
     */

    private String resolveErrorCode(
            Throwable throwable
    ) {

        if (
                throwable
                        instanceof DocumentParsingException
                        documentParsingException
        ) {

            return documentParsingException
                    .getCode()
                    .name();
        }

        if (
                throwable
                        instanceof CandidateExtractionException
        ) {

            return CANDIDATE_EXTRACTION_FAILED;
        }

        return PROCESSING_FAILED;
    }

    /*
     * ============================================================
     * ERROR MESSAGE
     * ============================================================
     */

    private String sanitizeErrorMessage(
            Throwable throwable
    ) {

        if (throwable == null) {

            return "Unknown processing failure";
        }

        String message =
                throwable.getMessage();

        if (
                message == null
                        || message.isBlank()
        ) {

            return throwable
                    .getClass()
                    .getSimpleName();
        }

        /*
         * Multi-line stack/payload DB-yə
         * düşməsin.
         */
        String normalized =
                message
                        .replaceAll(
                                "[\\r\\n]+",
                                " "
                        )
                        .trim();

        if (
                normalized.length()
                        <= MAX_ERROR_MESSAGE_LENGTH
        ) {

            return normalized;
        }

        return normalized.substring(
                0,
                MAX_ERROR_MESSAGE_LENGTH
        );
    }
}