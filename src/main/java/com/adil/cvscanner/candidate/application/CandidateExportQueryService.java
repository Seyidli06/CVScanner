package com.adil.cvscanner.candidate.application;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.candidate.infrastructure.CandidateSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CandidateExportQueryService {

    public static final int EXPORT_BATCH_SIZE =
            250;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "fullName",
                    "yearsOfExperience",
                    "preferredLocation",
                    "preferredJobType",
                    "sourceFilename"
            );

    private final EntityManager entityManager;

    private final CandidateRepository candidateRepository;

    public CandidateExportQueryService(
            EntityManager entityManager,
            CandidateRepository candidateRepository
    ) {

        this.entityManager =
                entityManager;

        this.candidateRepository =
                candidateRepository;
    }

    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    public void validateRequest(
            CandidateSearchCriteria criteria,
            String sortBy,
            String direction
    ) {

        /*
         * CandidateSearchCriteria öz business
         * validation-ını constructor daxilində edir.
         */

        validateSortField(
                sortBy
        );

        resolveDirection(
                direction
        );
    }

    /*
     * ============================================================
     * READ ONE EXPORT BATCH
     * ============================================================
     */

    public List<CandidateExportRow> readBatch(
            CandidateSearchCriteria criteria,
            String sortBy,
            String direction,
            int offset,
            int limit
    ) {

        validateRequest(
                criteria,
                sortBy,
                direction
        );

        if (
                offset < 0
        ) {

            throw new IllegalArgumentException(
                    "offset must not be negative"
            );
        }

        if (
                limit < 1
        ) {

            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }

        Sort.Direction sortDirection =
                resolveDirection(
                        direction
                );

        CriteriaBuilder criteriaBuilder =
                entityManager
                        .getCriteriaBuilder();

        CriteriaQuery<Candidate> query =
                criteriaBuilder
                        .createQuery(
                                Candidate.class
                        );

        Root<Candidate> root =
                query.from(
                        Candidate.class
                );

        /*
         * ========================================================
         * SAME FILTERING LOGIC AS CANDIDATE JSON API
         * ========================================================
         */

        Specification<Candidate> specification =
                CandidateSpecifications.from(
                        criteria
                );

        Predicate predicate =
                specification.toPredicate(
                        root,
                        query,
                        criteriaBuilder
                );

        if (
                predicate != null
        ) {

            query.where(
                    predicate
            );
        }

        query.select(
                root
        );

        /*
         * ========================================================
         * DETERMINISTIC SORT
         * ========================================================
         *
         * Primary:
         *
         * requested sort
         *
         * Secondary:
         *
         * id ASC
         */

        List<Order> orders =
                new ArrayList<>();

        if (
                sortDirection.isAscending()
        ) {

            orders.add(
                    criteriaBuilder.asc(
                            root.get(
                                    sortBy
                            )
                    )
            );

        } else {

            orders.add(
                    criteriaBuilder.desc(
                            root.get(
                                    sortBy
                            )
                    )
            );
        }

        orders.add(
                criteriaBuilder.asc(
                        root.get(
                                "id"
                        )
                )
        );

        query.orderBy(
                orders
        );

        TypedQuery<Candidate> typedQuery =
                entityManager.createQuery(
                        query
                );

        typedQuery.setFirstResult(
                offset
        );

        typedQuery.setMaxResults(
                limit
        );

        List<Candidate> candidates =
                typedQuery.getResultList();

        if (
                candidates.isEmpty()
        ) {

            return List.of();
        }

        /*
         * ========================================================
         * TWO-PHASE HYDRATION
         * ========================================================
         *
         * Pagination query-də collection fetch join etmirik.
         *
         * Sonra həmin batch üçün:
         *
         * upload + skills
         *
         * hydrate edirik.
         */

        List<UUID> candidateIds =
                candidates
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

        Map<UUID, Candidate> candidateById =
                new HashMap<>();

        for (
                Candidate candidate : hydratedCandidates
        ) {

            candidateById.put(
                    candidate.getId(),
                    candidate
            );
        }

        /*
         * Hydration IN query nəticə sırasını
         * guarantee etmir.
         *
         * Original ordered candidateIds üzrə
         * response-u yenidən qururuq.
         */

        List<CandidateExportRow> result =
                new ArrayList<>(
                        candidateIds.size()
                );

        for (
                UUID candidateId : candidateIds
        ) {

            Candidate candidate =
                    candidateById.get(
                            candidateId
                    );

            if (
                    candidate == null
            ) {

                throw new IllegalStateException(
                        "Candidate hydration result is incomplete"
                );
            }

            result.add(
                    toExportRow(
                            candidate
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    /*
     * ============================================================
     * CLEAR PERSISTENCE CONTEXT
     * ============================================================
     *
     * Long export zamanı əvvəlki batch entity-ləri
     * Hibernate first-level cache-də qalmasın.
     */

    public void clearPersistenceContext() {

        entityManager.clear();
    }

    /*
     * ============================================================
     * ENTITY -> EXPORT DTO
     * ============================================================
     */

    private CandidateExportRow toExportRow(
            Candidate candidate
    ) {

        List<String> skills =
                candidate
                        .getSkills()
                        .stream()
                        .sorted(
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .toList();

        return new CandidateExportRow(
                candidate.getId(),
                candidate
                        .getUpload()
                        .getId(),
                candidate.getFullName(),
                candidate.getYearsOfExperience(),
                candidate.getPreferredLocation(),
                candidate.getPreferredJobType(),
                skills,
                candidate.getSourceFilename()
        );
    }

    /*
     * ============================================================
     * SORT VALIDATION
     * ============================================================
     */

    private void validateSortField(
            String sortBy
    ) {

        if (
                sortBy == null
                        ||
                        !ALLOWED_SORT_FIELDS.contains(
                                sortBy
                        )
        ) {

            throw new InvalidCandidateQueryException(
                    "unsupported sort field"
            );
        }
    }

    private Sort.Direction resolveDirection(
            String direction
    ) {

        if (
                direction == null
        ) {

            throw new InvalidCandidateQueryException(
                    "direction must be 'asc' or 'desc'"
            );
        }

        if (
                direction.equalsIgnoreCase(
                        "asc"
                )
        ) {

            return Sort.Direction.ASC;
        }

        if (
                direction.equalsIgnoreCase(
                        "desc"
                )
        ) {

            return Sort.Direction.DESC;
        }

        throw new InvalidCandidateQueryException(
                "direction must be 'asc' or 'desc'"
        );
    }
}