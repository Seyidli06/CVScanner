package com.adil.cvscanner.candidate.infrastructure;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.application.CandidateExtractionException;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.processing.application.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedCandidateExtractorTest {

    private final RuleBasedCandidateExtractor extractor =
            new RuleBasedCandidateExtractor();

    @Test
    void shouldExtractCandidateInformation() {

        ParsedDocument document =
                document(
                        "john.pdf",
                        """
                        John Smith
                        Java Backend Developer

                        Location: Baku
                        Preferred work type: Remote

                        5 years of experience

                        Skills:
                        Java
                        Spring Boot
                        PostgreSQL
                        Redis
                        Docker
                        Kafka
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.fullName()
        ).isEqualTo(
                "John Smith"
        );

        assertThat(
                candidate.yearsOfExperience()
        ).isEqualTo(
                5
        );

        assertThat(
                candidate.preferredLocation()
        ).isEqualTo(
                "Baku"
        );

        assertThat(
                candidate.preferredJobType()
        ).isEqualTo(
                JobType.REMOTE
        );

        assertThat(
                candidate.skills()
        ).containsExactlyInAnyOrder(
                "Java",
                "Spring Boot",
                "PostgreSQL",
                "Redis",
                "Docker",
                "Kafka"
        );

        assertThat(
                candidate.skills()
        ).doesNotContain(
                "Spring"
        );

        assertThat(
                candidate.sourceFilename()
        ).isEqualTo(
                "john.pdf"
        );
    }

    @Test
    void shouldPreferExplicitNameOverJobTitle() {

        ParsedDocument document =
                document(
                        "candidate.pdf",
                        """
                        Senior Software Engineer
                        Full Name: Michael Brown

                        Location: Berlin
                        8 years experience

                        Java
                        Spring Boot
                        Docker
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.fullName()
        ).isEqualTo(
                "Michael Brown"
        );
    }

    @Test
    void shouldSkipJobTitleWhenFindingName() {

        ParsedDocument document =
                document(
                        "candidate.pdf",
                        """
                        Software Engineer
                        Michael Brown

                        Location: Berlin

                        Java
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.fullName()
        ).isEqualTo(
                "Michael Brown"
        );
    }

    @Test
    void shouldNotDetectJavaInsideJavaScript() {

        ParsedDocument document =
                document(
                        "frontend.pdf",
                        """
                        Jane Doe
                        Frontend Engineer

                        Skills:
                        JavaScript
                        TypeScript
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.skills()
        ).contains(
                "JavaScript",
                "TypeScript"
        );

        assertThat(
                candidate.skills()
        ).doesNotContain(
                "Java"
        );
    }

    @Test
    void shouldExtractQualifiedExperienceFormat() {

        ParsedDocument document =
                document(
                        "senior.pdf",
                        """
                        Robert Green
                        Software Architect

                        More than 9 years of professional experience

                        Java
                        PostgreSQL
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.yearsOfExperience()
        ).isEqualTo(
                9
        );
    }

    @Test
    void shouldUseHighestExperienceValueWhenMultipleMatchesExist() {

        ParsedDocument document =
                document(
                        "experience.pdf",
                        """
                        Robert Green

                        3 years experience with PostgreSQL.
                        7 years of professional experience overall.

                        Java
                        PostgreSQL
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.yearsOfExperience()
        ).isEqualTo(
                7
        );
    }

    @Test
    void shouldUseExplicitJobPreferenceInsteadOfRemoteMentionInExperience() {

        ParsedDocument document =
                document(
                        "hybrid.pdf",
                        """
                        Jane Doe
                        Backend Developer

                        Worked successfully with remote teams
                        across multiple countries.

                        Preferred work type: Hybrid

                        Java
                        Spring Boot
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.preferredJobType()
        ).isEqualTo(
                JobType.HYBRID
        );
    }

    @Test
    void shouldNotInferRemotePreferenceFromNormalSentence() {

        ParsedDocument document =
                document(
                        "unknown.pdf",
                        """
                        Jane Doe
                        Backend Developer

                        Worked with remote development teams.

                        Java
                        Redis
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.preferredJobType()
        ).isEqualTo(
                JobType.UNKNOWN
        );
    }

    @Test
    void shouldExtractBasedInLocation() {

        ParsedDocument document =
                document(
                        "location.pdf",
                        """
                        John Smith
                        Backend Developer

                        Based in Berlin

                        Java
                        Docker
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.preferredLocation()
        ).isEqualTo(
                "Berlin"
        );
    }

    @Test
    void shouldSupportUnicodeCandidateName() {

        ParsedDocument document =
                document(
                        "adil.pdf",
                        """
                        Adil Məmmədov
                        Backend Developer

                        Location: Bakı
                        Java
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.fullName()
        ).isEqualTo(
                "Adil Məmmədov"
        );

        assertThat(
                candidate.preferredLocation()
        ).isEqualTo(
                "Bakı"
        );
    }

    @Test
    void shouldReturnUnknownValuesWhenOptionalFieldsAreMissing() {

        ParsedDocument document =
                document(
                        "jane.pdf",
                        """
                        Jane Doe
                        Software Engineer
                        Java
                        """
                );

        CandidateDraft candidate =
                extractor.extract(
                        document
                );

        assertThat(
                candidate.fullName()
        ).isEqualTo(
                "Jane Doe"
        );

        assertThat(
                candidate.yearsOfExperience()
        ).isNull();

        assertThat(
                candidate.preferredLocation()
        ).isNull();

        assertThat(
                candidate.preferredJobType()
        ).isEqualTo(
                JobType.UNKNOWN
        );

        assertThat(
                candidate.skills()
        ).contains(
                "Java"
        );
    }

    @Test
    void shouldRejectEmptyDocument() {

        ParsedDocument document =
                document(
                        "empty.pdf",
                        "   "
                );

        assertThatThrownBy(
                () ->
                        extractor.extract(
                                document
                        )
        )
                .isInstanceOf(
                        CandidateExtractionException.class
                )
                .hasMessageContaining(
                        "empty document"
                );
    }

    @Test
    void shouldRejectDocumentWhenNameCannotBeDetermined() {

        ParsedDocument document =
                document(
                        "invalid.pdf",
                        """
                        12345678
                        experience
                        skills
                        https://example.com
                        test@example.com
                        """
                );

        assertThatThrownBy(
                () ->
                        extractor.extract(
                                document
                        )
        )
                .isInstanceOf(
                        CandidateExtractionException.class
                )
                .hasMessageContaining(
                        "full name"
                );
    }

    private ParsedDocument document(
            String filename,
            String text
    ) {

        return new ParsedDocument(
                Path.of(
                        filename
                ),
                "application/pdf",
                text
        );
    }
}
