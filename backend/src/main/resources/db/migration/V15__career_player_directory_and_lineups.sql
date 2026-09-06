CREATE TABLE career_player_directory (
    career_id VARCHAR(80) PRIMARY KEY REFERENCES career_save(career_id),
    directory_version VARCHAR(80) NOT NULL,
    directory_json CLOB NOT NULL,
    directory_hash CHAR(64) NOT NULL
);
CREATE TABLE career_roster_state (
    career_id VARCHAR(80) NOT NULL,
    season_year INTEGER NOT NULL,
    revision BIGINT NOT NULL,
    state_json CLOB NOT NULL,
    state_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id, season_year),
    FOREIGN KEY (career_id, season_year) REFERENCES career_season(career_id, season_year)
);
CREATE TABLE career_roster_command (
    client_command_id VARCHAR(80) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    source_year INTEGER NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    receipt_json CLOB NOT NULL,
    receipt_hash CHAR(64) NOT NULL,
    FOREIGN KEY (career_id, source_year) REFERENCES career_roster_state(career_id, season_year)
);
CREATE TABLE career_registered_player_pool (
    career_id VARCHAR(80) NOT NULL,
    season_year INTEGER NOT NULL,
    competition_id VARCHAR(80) NOT NULL,
    policy_version VARCHAR(80) NOT NULL,
    pool_json CLOB NOT NULL,
    pool_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id, season_year, competition_id),
    FOREIGN KEY (career_id, season_year) REFERENCES career_roster_state(career_id, season_year)
);
CREATE TABLE career_league_fixture_roster (
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    career_id VARCHAR(80) NOT NULL,
    season_year INTEGER NOT NULL,
    roster_revision BIGINT NOT NULL,
    roster_json CLOB NOT NULL,
    roster_hash CHAR(64) NOT NULL,
    PRIMARY KEY (season_id, fixture_id),
    FOREIGN KEY (season_id, fixture_id) REFERENCES league_fixture(season_id, fixture_id),
    FOREIGN KEY (career_id, season_year) REFERENCES career_roster_state(career_id, season_year)
);
