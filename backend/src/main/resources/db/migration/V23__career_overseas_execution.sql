-- Explicit introduction boundary; existing saves activate only next complete season.
CREATE TABLE career_overseas_activation (
    career_id VARCHAR(100) PRIMARY KEY,
    introduced_year INTEGER NOT NULL,
    activation_year INTEGER NOT NULL,
    introduced_on DATE NOT NULL,
    policy_version VARCHAR(100) NOT NULL,
    rule_json TEXT NOT NULL,
    rule_hash VARCHAR(64) NOT NULL,
    extension_applied BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE career_overseas_state (
    career_id VARCHAR(100) NOT NULL,
    season_year INTEGER NOT NULL,
    competition_id VARCHAR(100) NOT NULL,
    state_json TEXT NOT NULL,
    state_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY(career_id,season_year,competition_id)
);
