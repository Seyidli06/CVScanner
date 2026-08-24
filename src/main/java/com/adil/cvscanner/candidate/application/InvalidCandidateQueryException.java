package com.adil.cvscanner.candidate.application;

public class InvalidCandidateQueryException
        extends RuntimeException {

    public InvalidCandidateQueryException(
            String message
    ) {
        super(message);
    }
}