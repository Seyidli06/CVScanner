CREATE TABLE candidate
(
    id                  UUID PRIMARY KEY,

    upload_id           UUID NOT NULL,

    full_name           VARCHAR(255),

    years_of_experience INTEGER,

    preferred_location  VARCHAR(255),

    preferred_job_type  VARCHAR(50),

    source_filename     VARCHAR(255) NOT NULL,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_candidate_upload
        FOREIGN KEY (upload_id)
            REFERENCES cv_upload (id),

    CONSTRAINT chk_candidate_experience
        CHECK (
            years_of_experience IS NULL
                OR years_of_experience >= 0
            )
);

CREATE INDEX idx_candidate_upload_id
    ON candidate (upload_id);

CREATE INDEX idx_candidate_years_of_experience
    ON candidate (years_of_experience);

CREATE INDEX idx_candidate_preferred_job_type
    ON candidate (preferred_job_type);