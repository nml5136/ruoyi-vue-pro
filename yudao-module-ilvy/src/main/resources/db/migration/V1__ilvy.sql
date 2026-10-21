CREATE TABLE ilvy_place (id VARCHAR(80) PRIMARY KEY, payload TEXT NOT NULL);
CREATE TABLE ilvy_user_state (user_id BIGINT PRIMARY KEY, version BIGINT NOT NULL, payload TEXT NOT NULL);
CREATE TABLE ilvy_feedback (id VARCHAR(80) PRIMARY KEY, user_id BIGINT NOT NULL, place_id VARCHAR(80) NOT NULL, category VARCHAR(30) NOT NULL, description VARCHAR(2000) NOT NULL, status VARCHAR(30) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (place_id) REFERENCES ilvy_place(id));
CREATE INDEX ilvy_feedback_owner ON ilvy_feedback(user_id, created_at);
