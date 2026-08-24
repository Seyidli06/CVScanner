CREATE TABLE upload_storage_cleanup
(
    upload_id UUID PRIMARY KEY,
    cleaned_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_upload_storage_cleanup_upload
        FOREIGN KEY (upload_id)
            REFERENCES cv_upload (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_upload_storage_cleanup_cleaned_at
    ON upload_storage_cleanup (cleaned_at);