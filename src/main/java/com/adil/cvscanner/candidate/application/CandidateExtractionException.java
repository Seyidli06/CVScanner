package com.adil.cvscanner.candidate.application;

public class CandidateExtractionException
        extends RuntimeException {

    public CandidateExtractionException(
            String message
    ) {
        super(message);
    }

    public CandidateExtractionException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
