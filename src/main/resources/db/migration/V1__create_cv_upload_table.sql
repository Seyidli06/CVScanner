CREATE TABLE cv_upload
(
    id              UUID PRIMARY KEY,

    original_filename VARCHAR(255) NOT NULL,

    status          VARCHAR(30) NOT NULL,

    total_files     INTEGER NOT NULL DEFAULT 0,

    processed_files INTEGER NOT NULL DEFAULT 0,

    failed_files    INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    completed_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_cv_upload_total_files
        CHECK (total_files >= 0),

    CONSTRAINT chk_cv_upload_processed_files
        CHECK (processed_files >= 0),

    CONSTRAINT chk_cv_upload_failed_files
        CHECK (failed_files >= 0)
);