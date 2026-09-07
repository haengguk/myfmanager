ALTER TABLE career_player_directory ADD COLUMN lifecycle_version VARCHAR(100);
CREATE TABLE career_lifecycle_state (
 career_id VARCHAR(100) PRIMARY KEY REFERENCES career_save(career_id),
 revision BIGINT NOT NULL,
 state_json CLOB NOT NULL,
 state_hash VARCHAR(64) NOT NULL
);
CREATE TABLE career_generated_player (
 career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
 player_id VARCHAR(100) NOT NULL,
 intake_year INTEGER NOT NULL,
 created_on DATE NOT NULL,
 definition_json CLOB NOT NULL,
 definition_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(career_id,player_id)
);
CREATE TABLE career_lifecycle_review (
 career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
 season_year INTEGER NOT NULL,
 review_json CLOB NOT NULL,
 review_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(career_id,season_year)
);
ALTER TABLE career_development_season_close ADD COLUMN lifecycle_json CLOB;
ALTER TABLE career_development_season_close ADD COLUMN lifecycle_hash VARCHAR(64);
