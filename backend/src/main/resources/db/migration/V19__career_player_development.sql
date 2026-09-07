ALTER TABLE career_player_directory ADD COLUMN development_version VARCHAR(100);
CREATE TABLE career_development_state (
 career_id VARCHAR(100) PRIMARY KEY REFERENCES career_save(career_id),
 revision BIGINT NOT NULL,
 state_json CLOB NOT NULL,
 state_hash VARCHAR(64) NOT NULL
);
CREATE TABLE career_training_command (
 career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
 client_command_id VARCHAR(36) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 receipt_json CLOB NOT NULL,
 receipt_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(career_id,client_command_id)
);
CREATE TABLE career_development_binding (
 career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
 fixture_identity VARCHAR(300) NOT NULL,
 policy_version VARCHAR(100) NOT NULL,
 completion_hash VARCHAR(64),
 PRIMARY KEY(career_id,fixture_identity)
);
CREATE TABLE career_development_season_close (
 career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
 season_year INTEGER NOT NULL,
 closed_date DATE NOT NULL,
 state_json CLOB NOT NULL,
 state_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(career_id,season_year)
);
