package com.adil.cvscanner.candidate.domain;

import com.adil.cvscanner.upload.domain.CvUpload;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false)
    private CvUpload upload;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "preferred_location", length = 255)
    private String preferredLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_job_type", length = 50)
    private JobType preferredJobType;

    @Column(name = "source_filename", nullable = false, length = 255)
    private String sourceFilename;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ElementCollection
    @CollectionTable(
            name = "candidate_skill",
            joinColumns = @JoinColumn(name = "candidate_id")
    )
    @Column(name = "skill", nullable = false, length = 100)
    private Set<String> skills = new HashSet<>();

    protected Candidate() {
    }

    public Candidate(
            CvUpload upload,
            String fullName,
            Integer yearsOfExperience,
            String preferredLocation,
            JobType preferredJobType,
            String sourceFilename,
            Set<String> skills
    ) {
        this.upload = upload;
        this.fullName = fullName;
        this.yearsOfExperience = yearsOfExperience;
        this.preferredLocation = preferredLocation;
        this.preferredJobType = preferredJobType;
        this.sourceFilename = sourceFilename;
        this.createdAt = OffsetDateTime.now();

        if (skills != null) {
            this.skills.addAll(skills);
        }
    }

    public UUID getId() {
        return id;
    }

    public CvUpload getUpload() {
        return upload;
    }

    public String getFullName() {
        return fullName;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public String getPreferredLocation() {
        return preferredLocation;
    }

    public JobType getPreferredJobType() {
        return preferredJobType;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<String> getSkills() {
        return Set.copyOf(skills);
    }
}