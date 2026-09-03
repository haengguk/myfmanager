CREATE TABLE career_competition_cycle (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL CHECK (calendar_season_year >= 2026),
    cycle_schema VARCHAR(96) NOT NULL,
    rule_version VARCHAR(128) NOT NULL,
    rule_resource_hash CHAR(64) NOT NULL,
    game_policy_version VARCHAR(128) NOT NULL,
    projection_policy VARCHAR(128) NOT NULL,
    r3_r4_allocation_policy VARCHAR(128) NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    blocking_reason VARCHAR(128),
    r1_r2_import_hash CHAR(64),
    r1_r2_standings_revision BIGINT CHECK (r1_r2_standings_revision >= 0),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    state_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year),
    FOREIGN KEY (career_id) REFERENCES career_save(career_id)
);

CREATE TABLE career_competition_instance (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    rule_status VARCHAR(40) NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    blocking_reason VARCHAR(128),
    source_input_hash CHAR(64),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    state_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, competition_id),
    FOREIGN KEY (career_id, calendar_season_year)
        REFERENCES career_competition_cycle(career_id, calendar_season_year)
);

CREATE TABLE career_competition_seed (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    seed_scope VARCHAR(40) NOT NULL,
    seed_number INTEGER NOT NULL CHECK (seed_number >= 1),
    team_code VARCHAR(16) NOT NULL,
    imported_series_wins INTEGER NOT NULL CHECK (imported_series_wins >= 0),
    imported_series_losses INTEGER NOT NULL CHECK (imported_series_losses >= 0),
    imported_game_wins INTEGER NOT NULL CHECK (imported_game_wins >= 0),
    imported_game_losses INTEGER NOT NULL CHECK (imported_game_losses >= 0),
    source_input_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, competition_id,
        seed_scope, seed_number),
    UNIQUE (career_id, calendar_season_year, competition_id,
        seed_scope, team_code),
    FOREIGN KEY (career_id, calendar_season_year, competition_id)
        REFERENCES career_competition_instance(
            career_id, calendar_season_year, competition_id)
);

CREATE TABLE career_competition_fixture (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64) NOT NULL,
    fixture_id VARCHAR(96) NOT NULL,
    series_id VARCHAR(80) NOT NULL,
    scheduled_date DATE NOT NULL,
    schedule_status VARCHAR(48) NOT NULL,
    series_format VARCHAR(24) NOT NULL,
    hard_fearless BOOLEAN NOT NULL,
    first_selector_type VARCHAR(40) NOT NULL,
    first_selector_value VARCHAR(64) NOT NULL,
    second_selector_type VARCHAR(40) NOT NULL,
    second_selector_value VARCHAR(64) NOT NULL,
    first_team_code VARCHAR(16),
    second_team_code VARCHAR(16),
    execution_mode VARCHAR(32) NOT NULL,
    fixture_root_seed BIGINT NOT NULL,
    seed_algorithm VARCHAR(128) NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    winner_output_ids VARCHAR(256) NOT NULL,
    loser_output_ids VARCHAR(256) NOT NULL,
    winner_team_code VARCHAR(16),
    loser_team_code VARCHAR(16),
    completion_receipt_hash CHAR(64),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    PRIMARY KEY (career_id, calendar_season_year, competition_id, match_id),
    UNIQUE (fixture_id),
    UNIQUE (series_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id)
        REFERENCES career_competition_instance(
            career_id, calendar_season_year, competition_id)
);

CREATE INDEX idx_career_competition_fixture_date
    ON career_competition_fixture(
        career_id, calendar_season_year, scheduled_date, match_id);

CREATE TABLE career_competition_output (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    output_id VARCHAR(64) NOT NULL,
    team_code VARCHAR(16) NOT NULL,
    source_match_id VARCHAR(64) NOT NULL,
    source_receipt_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, output_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id, source_match_id)
        REFERENCES career_competition_fixture(
            career_id, calendar_season_year, competition_id, match_id)
);

CREATE TABLE career_competition_application (
    receipt_hash CHAR(64) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64) NOT NULL,
    series_id VARCHAR(80) NOT NULL,
    applied_revision BIGINT NOT NULL CHECK (applied_revision >= 1),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (career_id, calendar_season_year, competition_id, match_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id, match_id)
        REFERENCES career_competition_fixture(
            career_id, calendar_season_year, competition_id, match_id)
);

CREATE TABLE career_competition_operation_lock (
    lock_name VARCHAR(64) PRIMARY KEY,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO career_competition_operation_lock(lock_name, revision, updated_at)
VALUES ('COMPETITION_TRANSITIONS', 0, CURRENT_TIMESTAMP);
