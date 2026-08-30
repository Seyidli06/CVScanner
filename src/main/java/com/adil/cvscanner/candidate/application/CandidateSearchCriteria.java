package com.adil.cvscanner.candidate.application;

import com.adil.cvscanner.candidate.domain.JobType;

import java.util.UUID;

public record CandidateSearchCriteria(

        UUID uploadId,

        String skill,

        String location,

        JobType jobType,

        Integer minExperience,

        Integer maxExperience

) {

    public CandidateSearchCriteria {

        skill =
                normalizeNullableText(
                        skill
                );

        location =
                normalizeNullableText(
                        location
                );

        if (
                minExperience != null
                        && minExperience < 0
        ) {

            throw new InvalidCandidateQueryException(
                    "minExperience must not be negative"
            );
        }

        if (
                maxExperience != null
                        && maxExperience < 0
        ) {

            throw new InvalidCandidateQueryException(
                    "maxExperience must not be negative"
            );
        }

        if (
                minExperience != null
                        && maxExperience != null
                        && minExperience > maxExperience
        ) {

            throw new InvalidCandidateQueryException(
                    "minExperience must be less than or equal to maxExperience"
            );
        }
    }

    private static String normalizeNullableText(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        return value.trim();
    }
}
