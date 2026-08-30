package com.adil.cvscanner.common.api;

import com.adil.cvscanner.candidate.application.InvalidCandidateQueryException;
import com.adil.cvscanner.processing.application.CvProcessingLaunchException;
import com.adil.cvscanner.processing.application.InvalidProcessingFailureQueryException;
import com.adil.cvscanner.upload.application.InvalidUploadException;
import com.adil.cvscanner.upload.application.UploadNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(InvalidUploadException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidUpload(
            InvalidUploadException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_UPLOAD,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(UploadNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadNotFound(
            UploadNotFoundException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.UPLOAD_NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidCandidateQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCandidateQuery(
            InvalidCandidateQueryException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_CANDIDATE_QUERY,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidProcessingFailureQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProcessingFailureQuery(
            InvalidProcessingFailureQueryException exception,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_PROCESSING_FAILURE_QUERY,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(CvProcessingLaunchException.class)
    public ResponseEntity<ApiErrorResponse> handleProcessingLaunchFailure(
            CvProcessingLaunchException exception,
            HttpServletRequest request
    ) {

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.PROCESSING_LAUNCH_FAILED,
                "Unable to start CV processing",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String message =
                "Invalid value for parameter '"
                        + exception.getName()
                        + "'";

        return build(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST_PARAMETER,
                message,
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse body =
                ApiErrorResponse.of(
                        status,
                        code,
                        message,
                        request.getRequestURI()
                );

        return ResponseEntity
                .status(status)
                .body(body);
    }
}
