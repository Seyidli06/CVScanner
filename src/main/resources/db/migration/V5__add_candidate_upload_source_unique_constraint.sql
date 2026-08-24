ALTER TABLE candidate
    ADD CONSTRAINT uq_candidate_upload_source_filename
    UNIQUE (upload_id, source_filename);