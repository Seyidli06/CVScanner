CREATE TABLE processing_failure
(
    id            UUID PRIMARY KEY,

    upload_id     UUID NOT NULL,

    filename      VARCHAR(255) NOT NULL,

    error_code    VARCHAR(100) NOT NULL,

    error_message VARCHAR(1000),

    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_processing_failure_upload
        FOREIGN KEY (upload_id)
            REFERENCES cv_upload (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_processing_failure_upload_id
    ON processing_failure (upload_id);

CREATE INDEX idx_processing_failure_error_code
    ON processing_failure (error_code);