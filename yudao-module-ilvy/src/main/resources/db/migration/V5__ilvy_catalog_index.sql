CREATE TABLE ilvy_catalog_version (id INTEGER PRIMARY KEY, revision BIGINT NOT NULL);
INSERT INTO ilvy_catalog_version(id,revision) VALUES (1,0);
CREATE TABLE ilvy_place_index (
 place_id VARCHAR(80) PRIMARY KEY,
 payload_hash VARCHAR(64) NOT NULL,
 province VARBINARY(240) NOT NULL, city VARBINARY(240) NOT NULL, district VARBINARY(240) NOT NULL,
 grade_label VARBINARY(120) NOT NULL,
 search_text TEXT NOT NULL,
 drive_minutes INTEGER NULL, fee_amount DOUBLE NULL,
 age_min INTEGER NULL, age_max INTEGER NULL, stay_hours DOUBLE NULL,
 booking_needed BOOLEAN NOT NULL, stroller BOOLEAN NOT NULL,
 FOREIGN KEY (place_id) REFERENCES ilvy_place(id) ON DELETE CASCADE
);
CREATE INDEX ilvy_place_region ON ilvy_place_index(province,city,district,place_id);
CREATE INDEX ilvy_place_grade ON ilvy_place_index(grade_label,place_id);
CREATE TABLE ilvy_place_tag (
 place_id VARCHAR(80) NOT NULL, kind VARCHAR(10) NOT NULL, tag_value VARBINARY(120) NOT NULL,
 PRIMARY KEY(place_id,kind,tag_value),
 FOREIGN KEY(place_id) REFERENCES ilvy_place(id) ON DELETE CASCADE
);
CREATE INDEX ilvy_place_tag_search ON ilvy_place_tag(kind,tag_value,place_id);
