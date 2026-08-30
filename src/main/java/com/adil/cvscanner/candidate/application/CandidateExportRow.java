package com.adil.cvscanner.candidate.application;

import com.adil.cvscanner.candidate.domain.JobType;

import java.util.List;
import java.util.UUID;

public record CandidateExportRow(
        UUID candidateId,
        UUID uploadId,
        String fullName,
        Integer yearsOfExperience,
        String preferredLocation,
        JobType preferredJobType,
        List<String> skills,
        String sourceFilename
) {

    public CandidateExportRow {

        skills =
                skills == null
                        ? List.of()
                        : List.copyOf(
                        skills
                );
    }
}
