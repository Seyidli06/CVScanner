CREATE TABLE candidate_skill
(
    candidate_id UUID NOT NULL,

    skill         VARCHAR(100) NOT NULL,

    PRIMARY KEY (candidate_id, skill),

    CONSTRAINT fk_candidate_skill_candidate
        FOREIGN KEY (candidate_id)
            REFERENCES candidate (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_candidate_skill_skill
    ON candidate_skill (skill);