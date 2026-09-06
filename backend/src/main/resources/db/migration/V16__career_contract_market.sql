CREATE TABLE career_market_state (
    career_id VARCHAR(100) PRIMARY KEY REFERENCES career_save(career_id),
    revision BIGINT NOT NULL,
    state_json CLOB NOT NULL,
    state_hash VARCHAR(64) NOT NULL
);
CREATE TABLE career_market_command (
    career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
    client_command_id VARCHAR(36) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    receipt_json CLOB NOT NULL,
    receipt_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY(career_id, client_command_id)
);
CREATE TABLE career_market_season_close (
    career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
    season_year INTEGER NOT NULL,
    closed_date DATE NOT NULL,
    roster_json CLOB NOT NULL,
    market_json CLOB NOT NULL,
    result_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY(career_id, season_year)
);
CREATE TABLE career_registration_supplement (
    career_id VARCHAR(100) NOT NULL REFERENCES career_save(career_id),
    season_year INTEGER NOT NULL,
    competition_id VARCHAR(100) NOT NULL,
    team VARCHAR(40) NOT NULL,
    player_id VARCHAR(100) NOT NULL,
    position VARCHAR(20) NOT NULL,
    added_date DATE NOT NULL,
    revision BIGINT NOT NULL,
    reason VARCHAR(100) NOT NULL,
    PRIMARY KEY(career_id, season_year, competition_id, team, player_id)
);
