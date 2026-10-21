ALTER TABLE ilvy_user_state ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ilvy_user_state ADD COLUMN user_type INTEGER NOT NULL DEFAULT 1;
ALTER TABLE ilvy_user_state DROP PRIMARY KEY;
ALTER TABLE ilvy_user_state ADD PRIMARY KEY (tenant_id, user_type, user_id);
ALTER TABLE ilvy_feedback ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ilvy_feedback ADD COLUMN user_type INTEGER NOT NULL DEFAULT 1;
CREATE INDEX ilvy_feedback_scope ON ilvy_feedback(tenant_id, user_type, user_id, created_at);
