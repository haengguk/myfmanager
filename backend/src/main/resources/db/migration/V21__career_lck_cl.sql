ALTER TABLE career_player_directory ADD COLUMN cl_activation_year INTEGER;
ALTER TABLE career_player_directory ADD COLUMN cl_introduced_on DATE;
CREATE TABLE career_cl_state (
    career_id VARCHAR(80) NOT NULL,
    season_year INTEGER NOT NULL,
    state_json CLOB NOT NULL,
    state_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id,season_year),
    FOREIGN KEY (career_id,season_year) REFERENCES career_season(career_id,season_year)
);
CREATE TABLE career_cl_command (
    client_command_id VARCHAR(36) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    source_year INTEGER NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    receipt_json CLOB NOT NULL,
    receipt_hash CHAR(64) NOT NULL
);
CREATE TABLE career_appearance_performance (
    career_id VARCHAR(80) NOT NULL,
    completion_hash CHAR(64) NOT NULL,
    season_year INTEGER NOT NULL,
    performance_json CLOB NOT NULL,
    performance_hash CHAR(64) NOT NULL,
    PRIMARY KEY(career_id,completion_hash)
);

CREATE INDEX career_appearance_performance_season ON career_appearance_performance(career_id,season_year);
