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

    /*
     * Arbitrary entity property-ni request-dən
     * sort field kimi qəbul etmirik.
     */
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

    /*
     * ============================================================
     * SEARCH
     * ============================================================
     */

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

        /*
         * =====================================================
         * 1. VALIDATION
         * =====================================================
         */

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

        /*
         * =====================================================
         * 2. DETERMINISTIC SORT
         * =====================================================
         *
         * Primary:
         *
         * requested field
         *
         * Secondary:
         *
         * id ASC
         *
         *
         * Məsələn iki candidate-in:
         *
         * yearsOfExperience = 5
         *
         * olsa belə pagination stabil qalır.
         */

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

        /*
         * =====================================================
         * 3. PHASE ONE
         * PAGINATED QUERY
         * =====================================================
         *
         * Burada collection fetch etmirik.
         *
         * PostgreSQL LIMIT/OFFSET düzgün
         * işləyir.
         */

        Page<Candidate> candidatePage =
                candidateRepository
                        .findAll(
                                CandidateSpecifications.from(
                                        criteria
                                ),
                                pageable
                        );

        /*
         * Empty page-dirsə ikinci DB query
         * etməyin mənası yoxdur.
         */
        if (
                candidatePage.isEmpty()
        ) {

            return PageResponse.from(
                    candidatePage,
                    CandidateResponse::from
            );
        }

        /*
         * =====================================================
         * 4. PAGE IDS
         * =====================================================
         */

        List<UUID> candidateIds =
                candidatePage
                        .getContent()
                        .stream()
                        .map(
                                Candidate::getId
                        )
                        .toList();

        /*
         * =====================================================
         * 5. PHASE TWO
         * BATCH HYDRATION
         * =====================================================
         *
         * Bir SQL:
         *
         * WHERE candidate.id IN (...)
         *
         * +
         *
         * upload
         * skills
         *
         * fetch edilir.
         */

        List<Candidate> hydratedCandidates =
                candidateRepository
                        .findAllWithResponseDetailsByIdIn(
                                candidateIds
                        );

        /*
         * Hydration query nəticəsinin order-i
         * page order-i olmaq məcburiyyətində
         * deyil.
         *
         * Buna görə id -> Candidate map
         * qururuq.
         */

        Map<UUID, Candidate> hydratedById =
                indexById(
                        hydratedCandidates
                );

        /*
         * =====================================================
         * 6. DTO MAPPING
         * =====================================================
         *
         * PageResponse original page order-i
         * qoruyur.
         *
         * Mapper isə həmin ID-nin fully
         * hydrated entity-sini istifadə edir.
         */

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

    /*
     * ============================================================
     * HYDRATED INDEX
     * ============================================================
     */

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

            /*
             * JPQL distinct səbəbilə normal
             * halda duplicate olmamalıdır.
             *
             * Yenə də invariant-i qoruyuruq.
             */
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

    /*
     * ============================================================
     * HYDRATION INVARIANT
     * ============================================================
     *
     * Phase one-da Candidate tapılıbsa,
     * həmin transaction daxilində phase two
     * query-də də tapılmalıdır.
     *
     * Əks halda response-u səssiz şəkildə
     * yarımçıq qaytarmırıq.
     */

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

    /*
     * ============================================================
     * PAGINATION VALIDATION
     * ============================================================
     */

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

    /*
     * ============================================================
     * SORT VALIDATION
     * ============================================================
     */

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

    /*
     * ============================================================
     * DIRECTION
     * ============================================================
     */

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