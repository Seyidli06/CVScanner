package com.adil.cvscanner.candidate.infrastructure;

import com.adil.cvscanner.candidate.application.CandidateSearchCriteria;
import com.adil.cvscanner.candidate.domain.Candidate;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.SetJoin;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CandidateSpecifications {

    private CandidateSpecifications() {
    }

    public static Specification<Candidate> from(
            CandidateSearchCriteria criteria
    ) {

        List<Specification<Candidate>> specifications =
                new ArrayList<>();

        if (
                criteria.uploadId() != null
        ) {

            specifications.add(
                    hasUploadId(
                            criteria.uploadId()
                    )
            );
        }

        if (
                criteria.skill() != null
        ) {

            specifications.add(
                    hasSkill(
                            criteria.skill()
                    )
            );
        }

        if (
                criteria.location() != null
        ) {

            specifications.add(
                    hasLocation(
                            criteria.location()
                    )
            );
        }

        if (
                criteria.jobType() != null
        ) {

            specifications.add(
                    hasJobType(
                            criteria.jobType()
                    )
            );
        }

        if (
                criteria.minExperience() != null
        ) {

            specifications.add(
                    experienceAtLeast(
                            criteria.minExperience()
                    )
            );
        }

        if (
                criteria.maxExperience() != null
        ) {

            specifications.add(
                    experienceAtMost(
                            criteria.maxExperience()
                    )
            );
        }

        return Specification.allOf(
                specifications
        );
    }

    private static Specification<Candidate>
    hasUploadId(
            java.util.UUID uploadId
    ) {

        return (
                root,
                query,
                builder
        ) ->
                builder.equal(
                        root.get(
                                        "upload"
                                )
                                .get(
                                        "id"
                                ),
                        uploadId
                );
    }

    private static Specification<Candidate>
    hasSkill(
            String skill
    ) {

        String normalizedSkill =
                skill
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return (
                root,
                query,
                builder
        ) -> {

            SetJoin<Candidate, String> skills =
                    root.joinSet(
                            "skills",
                            JoinType.INNER
                    );

            query.distinct(
                    true
            );

            return builder.equal(
                    builder.lower(
                            skills
                    ),
                    normalizedSkill
            );
        };
    }

    private static Specification<Candidate>
    hasLocation(
            String location
    ) {

        String normalizedLocation =
                location
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return (
                root,
                query,
                builder
        ) ->
                builder.equal(
                        builder.lower(
                                root.get(
                                        "preferredLocation"
                                )
                        ),
                        normalizedLocation
                );
    }

    private static Specification<Candidate>
    hasJobType(
            com.adil.cvscanner.candidate.domain.JobType jobType
    ) {

        return (
                root,
                query,
                builder
        ) ->
                builder.equal(
                        root.get(
                                "preferredJobType"
                        ),
                        jobType
                );
    }

    private static Specification<Candidate>
    experienceAtLeast(
            int minExperience
    ) {

        return (
                root,
                query,
                builder
        ) ->
                builder.greaterThanOrEqualTo(
                        root.get(
                                "yearsOfExperience"
                        ),
                        minExperience
                );
    }

    private static Specification<Candidate>
    experienceAtMost(
            int maxExperience
    ) {

        return (
                root,
                query,
                builder
        ) ->
                builder.lessThanOrEqualTo(
                        root.get(
                                "yearsOfExperience"
                        ),
                        maxExperience
                );
    }
}
