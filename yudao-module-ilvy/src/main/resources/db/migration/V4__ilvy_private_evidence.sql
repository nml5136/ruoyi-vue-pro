CREATE TABLE ilvy_feedback_evidence (
 id VARCHAR(80) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL,
 user_type INTEGER NOT NULL DEFAULT 1,
 content_type VARCHAR(40) NOT NULL,
 file_path VARCHAR(500) NOT NULL,
 file_size BIGINT NOT NULL,
 sha256 VARCHAR(64) NOT NULL,
 feedback_id VARCHAR(80) NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (feedback_id) REFERENCES ilvy_feedback(id)
);
CREATE INDEX ilvy_evidence_owner ON ilvy_feedback_evidence(tenant_id,user_type,user_id,feedback_id);
