package com.adil.cvscanner.candidate.application;

import com.adil.cvscanner.candidate.api.CandidateResponse;
import com.adil.cvscanner.candidate.api.PageResponse;
import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.candidate.infrastructure.CandidateSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CandidateQueryService {

    private static final int MAX_PAGE_SIZE =
            100;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "fullName",
                    "yearsOfExperience",
                    "preferredLocation",
                    "preferredJobType",
                    "sourceFilename"
            );

    private final CandidateRepository
            candidateRepository;

    public CandidateQueryService(
            CandidateRepository candidateRepository
    ) {

        this.candidateRepository =
                candidateRepository;
    }

    @Transactional(
            readOnly = true
    )
    public PageResponse<CandidateResponse> search(
            CandidateSearchCriteria criteria,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        validatePagination(
                page,
                size
        );

        String normalizedSortBy =
                validateSortField(
                        sortBy
                );

        Sort.Direction sortDirection =
                parseDirection(
                        direction
                );

        Sort sort =
                Sort.by(
                                sortDirection,
                                normalizedSortBy
                        )
                        .and(
                                Sort.by(
                                        Sort.Direction.ASC,
                                        "id"
                                )
                        );

        PageRequest pageable =
                PageRequest.of(
                        page,
                        size,
                        sort
                );

        Page<Candidate> candidatePage =
                candidateRepository
                        .findAll(
                                CandidateSpecifications.from(
                                        criteria
                                ),
                                pageable
                        );

        if (
                candidatePage.isEmpty()
        ) {

            return PageResponse.from(
                    candidatePage,
                    CandidateResponse::from
            );
        }

        List<UUID> candidateIds =
                candidatePage
                        .getContent()
                        .stream()
                        .map(
                                Candidate::getId
                        )
                        .toList();

        List<Candidate> hydratedCandidates =
                candidateRepository
                        .findAllWithResponseDetailsByIdIn(
                                candidateIds
                        );

        Map<UUID, Candidate> hydratedById =
                indexById(
                        hydratedCandidates
                );

        return PageResponse.from(
                candidatePage,
                candidate ->
                        CandidateResponse.from(
                                requireHydratedCandidate(
                                        candidate.getId(),
                                        hydratedById
                                )
                        )
        );
    }

    private Map<UUID, Candidate> indexById(
            Collection<Candidate> candidates
    ) {

        Map<UUID, Candidate> result =
                new HashMap<>();

        for (
                Candidate candidate
                : candidates
        ) {

            Candidate previous =
                    result.put(
                            candidate.getId(),
                            candidate
                    );

            if (
                    previous != null
            ) {

                throw new IllegalStateException(
                        "Duplicate hydrated Candidate result: "
                                + candidate.getId()
                );
            }
        }

        return result;
    }

    private Candidate requireHydratedCandidate(
            UUID candidateId,
            Map<UUID, Candidate> hydratedById
    ) {

        Candidate candidate =
                hydratedById.get(
                        candidateId
                );

        if (
                candidate == null
        ) {

            throw new IllegalStateException(
                    "Candidate disappeared during response hydration: "
                            + candidateId
            );
        }

        return candidate;
    }

    private void validatePagination(
            int page,
            int size
    ) {

        if (
                page < 0
        ) {

            throw new InvalidCandidateQueryException(
                    "page must not be negative"
            );
        }

        if (
                size < 1
        ) {

            throw new InvalidCandidateQueryException(
                    "size must be at least 1"
            );
        }

        if (
                size > MAX_PAGE_SIZE
        ) {

            throw new InvalidCandidateQueryException(
                    "size must not exceed "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private String validateSortField(
            String sortBy
    ) {

        String effectiveSortBy =
                (
                        sortBy == null
                                || sortBy.isBlank()
                )
                        ? "fullName"
                        : sortBy.trim();

        if (
                !ALLOWED_SORT_FIELDS.contains(
                        effectiveSortBy
                )
        ) {

            throw new InvalidCandidateQueryException(
                    "Unsupported sortBy value: "
                            + effectiveSortBy
            );
        }

        return effectiveSortBy;
    }

    private Sort.Direction parseDirection(
            String direction
    ) {

        if (
                direction == null
                        || direction.isBlank()
        ) {

            return Sort.Direction.ASC;
        }

        return switch (
                direction
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
                ) {

            case "asc" ->
                    Sort.Direction.ASC;

            case "desc" ->
                    Sort.Direction.DESC;

            default ->
                    throw new InvalidCandidateQueryException(
                            "direction must be either asc or desc"
                    );
        };
    }
}
