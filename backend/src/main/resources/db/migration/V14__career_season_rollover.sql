CREATE TABLE career_season (
    career_id VARCHAR(80) NOT NULL,
    season_year INTEGER NOT NULL,
    season_ordinal INTEGER NOT NULL CHECK (season_ordinal > 0),
    league_id VARCHAR(80) NOT NULL,
    season_id VARCHAR(80) NOT NULL UNIQUE,
    season_root_seed BIGINT NOT NULL,
    frozen_snapshot_hash CHAR(64) NOT NULL,
    product_decision_hash CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL,
    roster_json CLOB,
    roster_hash CHAR(64),
    closed_calendar_json CLOB,
    closed_result_hash CHAR(64),
    PRIMARY KEY (career_id, season_year),
    UNIQUE (career_id, season_ordinal),
    FOREIGN KEY (career_id) REFERENCES career_save(career_id),
    FOREIGN KEY (league_id, season_id) REFERENCES league_season(league_id, season_id)
);
INSERT INTO career_season(career_id, season_year, season_ordinal, league_id, season_id,
    season_root_seed, frozen_snapshot_hash, product_decision_hash, lifecycle_status)
SELECT s.career_id, c.active_calendar_season_year, 1, s.league_id, s.season_id,
    s.career_root_seed, s.league_frozen_snapshot_hash, s.league_product_decision_hash, 'ACTIVE'
FROM career_save s JOIN career_calendar_state c ON c.career_id = s.career_id;

CREATE TABLE career_season_transition (
    client_command_id VARCHAR(80) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    source_year INTEGER NOT NULL,
    expected_revision BIGINT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    destination_year INTEGER NOT NULL,
    destination_season_id VARCHAR(80) NOT NULL,
    resulting_revision BIGINT NOT NULL,
    result_hash CHAR(64) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (career_id, source_year),
    FOREIGN KEY (career_id, source_year) REFERENCES career_season(career_id, season_year),
    FOREIGN KEY (career_id, destination_year) REFERENCES career_season(career_id, season_year)
);
