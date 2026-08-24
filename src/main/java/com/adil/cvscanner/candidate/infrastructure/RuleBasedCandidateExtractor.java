package com.adil.cvscanner.candidate.infrastructure;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.application.CandidateExtractionException;
import com.adil.cvscanner.candidate.application.CandidateExtractor;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.processing.application.ParsedDocument;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedCandidateExtractor
        implements CandidateExtractor {

    /*
     * ============================================================
     * NAME
     * ============================================================
     *
     * Explicit label varsa ona üstünlük veririk:
     *
     * Name: John Smith
     * Full Name: John Smith
     */
    private static final Pattern NAME_LABEL_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*(?:full\\s+name|name)"
                            + "\\s*[:\\-]\\s*"
                            + "([\\p{L}][\\p{L}'’.-]*"
                            + "(?:\\s+[\\p{L}][\\p{L}'’.-]*){1,4})"
                            + "\\s*$"
            );

    /*
     * ============================================================
     * EXPERIENCE
     * ============================================================
     *
     * Dəstəkləyir:
     *
     * 5 years experience
     * 5 years of experience
     * 5+ years experience
     * 5 yrs experience
     * 5 years professional experience
     * 5 years of professional experience
     */
    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile(
                    "(?i)\\b"
                            + "(\\d{1,2})"
                            + "\\+?"
                            + "\\s*"
                            + "(?:years?|yrs?)"
                            + "\\s+"
                            + "(?:of\\s+)?"
                            + "(?:professional\\s+)?"
                            + "experience"
                            + "\\b"
            );

    /*
     * Məsələn:
     *
     * Over 5 years of experience
     * More than 7 years experience
     * At least 3 years of professional experience
     */
    private static final Pattern QUALIFIED_EXPERIENCE_PATTERN =
            Pattern.compile(
                    "(?i)\\b"
                            + "(?:over|more\\s+than|at\\s+least)"
                            + "\\s+"
                            + "(\\d{1,2})"
                            + "\\s*"
                            + "(?:years?|yrs?)"
                            + "\\s+"
                            + "(?:of\\s+)?"
                            + "(?:professional\\s+)?"
                            + "experience"
                            + "\\b"
            );

    /*
     * ============================================================
     * LOCATION
     * ============================================================
     */

    private static final Pattern LOCATION_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*"
                            + "(?:(?:preferred|current)\\s+)?"
                            + "location"
                            + "\\s*[:\\-]\\s*"
                            + "([^\\r\\n]{2,100})"
                            + "\\s*$"
            );

    private static final Pattern BASED_IN_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*"
                            + "(?:based\\s+in|located\\s+in)"
                            + "\\s*[:\\-]?\\s*"
                            + "([^\\r\\n]{2,100})"
                            + "\\s*$"
            );

    /*
     * ============================================================
     * JOB TYPE
     * ============================================================
     *
     * Əvvəl explicit preference axtarırıq.
     *
     * Bunun səbəbi:
     *
     * "worked with remote teams"
     *
     * cümləsinin avtomatik REMOTE preference
     * sayılmamasıdır.
     */
    private static final Pattern JOB_TYPE_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*"
                            + "(?:"
                            + "preferred\\s+(?:work\\s+)?(?:type|arrangement)"
                            + "|work\\s+(?:type|preference|arrangement)"
                            + "|job\\s+type"
                            + ")"
                            + "\\s*[:\\-]\\s*"
                            + "(remote|hybrid|on[-\\s]?site)"
                            + "\\s*$"
            );

    /*
     * ============================================================
     * NAME FALSE-POSITIVE PROTECTION
     * ============================================================
     */

    private static final Set<String> SECTION_HEADINGS =
            Set.of(
                    "curriculum vitae",
                    "resume",
                    "cv",
                    "profile",
                    "summary",
                    "professional summary",
                    "personal information",
                    "contact",
                    "contact information",
                    "experience",
                    "work experience",
                    "professional experience",
                    "education",
                    "skills",
                    "technical skills",
                    "certifications",
                    "projects",
                    "languages"
            );

    /*
     * Əgər ilk line bunlardan birini daşıyırsa,
     * böyük ehtimalla şəxs adı deyil,
     * job title-dır.
     */
    private static final Set<String> JOB_TITLE_WORDS =
            Set.of(
                    "developer",
                    "engineer",
                    "architect",
                    "manager",
                    "specialist",
                    "analyst",
                    "consultant",
                    "administrator",
                    "designer",
                    "director",
                    "intern",
                    "student",
                    "devops",
                    "tester",
                    "lead"
            );

    /*
     * ============================================================
     * SKILLS
     * ============================================================
     *
     * Controlled dictionary.
     *
     * Phase 6 üçün deterministic saxlayırıq.
     */
    private static final List<String> KNOWN_SKILLS =
            List.of(
                    "Spring Boot",
                    "Spring",
                    "JavaScript",
                    "TypeScript",
                    "PostgreSQL",
                    "RabbitMQ",
                    "Microservices",
                    "Kubernetes",
                    "Hibernate",
                    "GraphQL",
                    "MongoDB",
                    "Docker",
                    "Gradle",
                    "Oracle",
                    "Redis",
                    "Kafka",
                    "Maven",
                    "MySQL",
                    "Azure",
                    "Java",
                    "JPA",
                    "REST",
                    "Git",
                    "AWS"
            );

    @Override
    public CandidateDraft extract(
            ParsedDocument document
    ) {

        if (document == null) {
            throw new CandidateExtractionException(
                    "ParsedDocument must not be null"
            );
        }

        String text =
                document.text();

        if (
                text == null
                        || text.isBlank()
        ) {

            throw new CandidateExtractionException(
                    "Cannot extract candidate from empty document: "
                            + document.source()
            );
        }

        String fullName =
                extractFullName(
                        text
                );

        Integer yearsOfExperience =
                extractYearsOfExperience(
                        text
                );

        String preferredLocation =
                extractPreferredLocation(
                        text
                );

        JobType preferredJobType =
                extractPreferredJobType(
                        text
                );

        Set<String> skills =
                extractSkills(
                        text
                );

        String sourceFilename =
                extractSourceFilename(
                        document.source()
                );

        return new CandidateDraft(
                fullName,
                yearsOfExperience,
                preferredLocation,
                preferredJobType,
                skills,
                sourceFilename
        );
    }

    /*
     * ============================================================
     * FULL NAME
     * ============================================================
     */

    private String extractFullName(
            String text
    ) {

        /*
         * 1. Ən güclü signal:
         *
         * Name: John Smith
         */
        Matcher explicitNameMatcher =
                NAME_LABEL_PATTERN.matcher(
                        text
                );

        if (
                explicitNameMatcher.find()
        ) {

            return normalizeWhitespace(
                    explicitNameMatcher.group(1)
            );
        }

        /*
         * 2. Explicit label yoxdursa,
         * meaningful lines arasında
         * plausible person name axtarırıq.
         */
        return text
                .lines()
                .map(String::trim)
                .filter(
                        line ->
                                !line.isBlank()
                )
                .filter(
                        this::isPossibleName
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new CandidateExtractionException(
                                        "Candidate full name could not be extracted"
                                )
                );
    }

    private boolean isPossibleName(
            String line
    ) {

        String normalized =
                normalizeWhitespace(
                        line
                );

        if (
                normalized.length() < 3
                        || normalized.length() > 100
        ) {
            return false;
        }

        String lowerCase =
                normalized.toLowerCase(
                        Locale.ROOT
                );

        /*
         * Section heading deyil.
         */
        if (
                SECTION_HEADINGS.contains(
                        lowerCase
                )
        ) {
            return false;
        }

        /*
         * Contact məlumatı deyil.
         */
        if (
                normalized.contains("@")
                        || lowerCase.startsWith(
                        "http://"
                )
                        || lowerCase.startsWith(
                        "https://"
                )
                        || lowerCase.startsWith(
                        "www."
                )
        ) {
            return false;
        }

        /*
         * Telefon, tarix və s.
         */
        if (
                normalized
                        .chars()
                        .anyMatch(
                                Character::isDigit
                        )
        ) {
            return false;
        }

        String[] words =
                normalized.split(
                        "\\s+"
                );

        /*
         * MVP rule:
         *
         * insan adı minimum 2,
         * maksimum 5 hissədən ibarət olsun.
         */
        if (
                words.length < 2
                        || words.length > 5
        ) {
            return false;
        }

        /*
         * Hər token person-name kimi görünməlidir.
         */
        for (
                String word : words
        ) {

            if (
                    !isNameWord(
                            word
                    )
            ) {
                return false;
            }

            if (
                    JOB_TITLE_WORDS.contains(
                            word.toLowerCase(
                                    Locale.ROOT
                            )
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    private boolean isNameWord(
            String word
    ) {

        /*
         * Unicode letters dəstəyi:
         *
         * Ə
         * Ş
         * Ç
         * Ö
         * Ü
         * Ğ
         * İ
         *
         * və s.
         */
        return word.matches(
                "[\\p{L}][\\p{L}'’.-]*"
        );
    }

    /*
     * ============================================================
     * EXPERIENCE
     * ============================================================
     */

    private Integer extractYearsOfExperience(
            String text
    ) {

        Integer regularExperience =
                findHighestExperience(
                        EXPERIENCE_PATTERN,
                        text
                );

        Integer qualifiedExperience =
                findHighestExperience(
                        QUALIFIED_EXPERIENCE_PATTERN,
                        text
                );

        if (
                regularExperience == null
        ) {
            return qualifiedExperience;
        }

        if (
                qualifiedExperience == null
        ) {
            return regularExperience;
        }

        /*
         * CV-də bir neçə experience ifadəsi
         * varsa ən böyük uyğun dəyəri götürürük.
         */
        return Math.max(
                regularExperience,
                qualifiedExperience
        );
    }

    private Integer findHighestExperience(
            Pattern pattern,
            String text
    ) {

        Matcher matcher =
                pattern.matcher(
                        text
                );

        Integer highest =
                null;

        while (
                matcher.find()
        ) {

            int value;

            try {

                value =
                        Integer.parseInt(
                                matcher.group(1)
                        );

            } catch (
                    NumberFormatException exception
            ) {

                throw new CandidateExtractionException(
                        "Invalid years of experience value",
                        exception
                );
            }

            if (
                    highest == null
                            || value > highest
            ) {

                highest =
                        value;
            }
        }

        return highest;
    }

    /*
     * ============================================================
     * LOCATION
     * ============================================================
     */

    private String extractPreferredLocation(
            String text
    ) {

        String location =
                findFirstGroup(
                        LOCATION_PATTERN,
                        text
                );

        if (
                location != null
        ) {
            return location;
        }

        return findFirstGroup(
                BASED_IN_PATTERN,
                text
        );
    }

    private String findFirstGroup(
            Pattern pattern,
            String text
    ) {

        Matcher matcher =
                pattern.matcher(
                        text
                );

        if (
                !matcher.find()
        ) {
            return null;
        }

        String value =
                normalizeWhitespace(
                        matcher.group(1)
                );

        return value.isBlank()
                ? null
                : value;
    }

    /*
     * ============================================================
     * JOB TYPE
     * ============================================================
     */

    private JobType extractPreferredJobType(
            String text
    ) {

        /*
         * 1. Əvvəl explicit preference.
         */
        Matcher matcher =
                JOB_TYPE_PATTERN.matcher(
                        text
                );

        if (
                matcher.find()
        ) {

            return mapJobType(
                    matcher.group(1)
            );
        }

        /*
         * 2. Bəzi CV-lərdə ayrıca line olur:
         *
         * Remote
         *
         * Hybrid
         *
         * On-site
         *
         * Amma sadəcə normal sentence içindəki
         * "remote" sözünü götürmürük.
         */
        for (
                String line : text.lines().toList()
        ) {

            String normalizedLine =
                    line.trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (
                    normalizedLine.equals(
                            "remote"
                    )
            ) {
                return JobType.REMOTE;
            }

            if (
                    normalizedLine.equals(
                            "hybrid"
                    )
            ) {
                return JobType.HYBRID;
            }

            if (
                    normalizedLine.equals(
                            "onsite"
                    )
                            || normalizedLine.equals(
                            "on-site"
                    )
                            || normalizedLine.equals(
                            "on site"
                    )
            ) {
                return JobType.ONSITE;
            }
        }

        return JobType.UNKNOWN;
    }

    private JobType mapJobType(
            String value
    ) {

        String normalized =
                value
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replace(
                                "-",
                                ""
                        )
                        .replace(
                                " ",
                                ""
                        );

        return switch (normalized) {

            case "remote" ->
                    JobType.REMOTE;

            case "hybrid" ->
                    JobType.HYBRID;

            case "onsite" ->
                    JobType.ONSITE;

            default ->
                    JobType.UNKNOWN;
        };
    }

    /*
     * ============================================================
     * SKILLS
     * ============================================================
     */

    private Set<String> extractSkills(
            String text
    ) {

        Set<String> detectedSkills =
                new LinkedHashSet<>();

        for (
                String skill : KNOWN_SKILLS
        ) {

            if (
                    containsSkill(
                            text,
                            skill
                    )
            ) {

                detectedSkills.add(
                        skill
                );
            }
        }

        /*
         * Spring Boot varsa generic Spring-i
         * ayrıca saxlamaq istəmirik.
         */
        if (
                detectedSkills.contains(
                        "Spring Boot"
                )
        ) {

            detectedSkills.remove(
                    "Spring"
            );
        }

        return detectedSkills;
    }

    private boolean containsSkill(
            String text,
            String skill
    ) {

        /*
         * Raw String.contains istifadə etmirik.
         *
         * Əks halda:
         *
         * JavaScript
         *
         * içindən:
         *
         * Java
         *
         * səhvən tapıla bilər.
         *
         * Sol və sağ tərəfdə letter/digit
         * boundary yoxlayırıq.
         */
        Pattern pattern =
                Pattern.compile(
                        "(?iu)"
                                + "(?<![\\p{L}\\p{N}])"
                                + Pattern.quote(
                                skill
                        )
                                + "(?![\\p{L}\\p{N}])"
                );

        return pattern
                .matcher(
                        text
                )
                .find();
    }

    /*
     * ============================================================
     * SOURCE
     * ============================================================
     */

    private String extractSourceFilename(
            Path source
    ) {

        if (
                source == null
        ) {

            throw new CandidateExtractionException(
                    "Document source path must not be null"
            );
        }

        Path filename =
                source.getFileName();

        if (
                filename == null
        ) {

            throw new CandidateExtractionException(
                    "Document source filename could not be determined: "
                            + source
            );
        }

        return filename.toString();
    }

    /*
     * ============================================================
     * COMMON
     * ============================================================
     */

    private String normalizeWhitespace(
            String value
    ) {

        return value
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }
}