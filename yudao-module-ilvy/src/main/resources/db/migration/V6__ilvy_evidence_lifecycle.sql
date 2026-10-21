ALTER TABLE ilvy_feedback_evidence ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE ilvy_feedback_evidence ADD COLUMN purged_at TIMESTAMP NULL;
ALTER TABLE ilvy_feedback_evidence ADD COLUMN purge_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ilvy_feedback_evidence ADD COLUMN last_purge_error VARCHAR(80) NULL;
CREATE INDEX ilvy_evidence_expiry ON ilvy_feedback_evidence(deleted_at,purged_at,created_at);
