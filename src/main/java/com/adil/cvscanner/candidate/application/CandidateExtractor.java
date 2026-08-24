package com.adil.cvscanner.candidate.application;

import com.adil.cvscanner.processing.application.ParsedDocument;

public interface CandidateExtractor {

    CandidateDraft extract(
            ParsedDocument document
    );
}