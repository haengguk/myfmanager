CREATE TABLE career_lck_final_ranking_snapshot (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL CHECK (calendar_season_year >= 2026),
    season_ordinal INTEGER NOT NULL CHECK (season_ordinal >= 1),
    source_season_id VARCHAR(80) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    state_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year),
    UNIQUE (career_id, season_ordinal),
    FOREIGN KEY (career_id) REFERENCES career_save(career_id),
    CHECK (lifecycle_status = 'SEALED')
);

CREATE TABLE career_lck_final_ranking_row (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    rank_number INTEGER NOT NULL CHECK (rank_number BETWEEN 1 AND 10),
    team_code VARCHAR(16) NOT NULL,
    series_wins INTEGER NOT NULL CHECK (series_wins >= 0),
    series_losses INTEGER NOT NULL CHECK (series_losses >= 0),
    game_wins INTEGER NOT NULL CHECK (game_wins >= 0),
    game_losses INTEGER NOT NULL CHECK (game_losses >= 0),
    PRIMARY KEY (career_id, calendar_season_year, rank_number),
    UNIQUE (career_id, calendar_season_year, team_code),
    FOREIGN KEY (career_id, calendar_season_year)
        REFERENCES career_lck_final_ranking_snapshot(career_id, calendar_season_year)
);

CREATE TABLE career_competition_series_binding (
    binding_hash CHAR(64) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64) NOT NULL,
    fixture_id VARCHAR(96) NOT NULL,
    series_id VARCHAR(80) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    binding_schema VARCHAR(80) NOT NULL,
    binding_canonical CLOB NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    completion_receipt_hash CHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (career_id, calendar_season_year, competition_id, match_id),
    UNIQUE (series_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id, match_id)
        REFERENCES career_competition_fixture(
            career_id, calendar_season_year, competition_id, match_id)
);

CREATE TABLE career_competition_series_checkpoint (
    binding_hash CHAR(64) PRIMARY KEY,
    series_id VARCHAR(80) NOT NULL UNIQUE,
    checkpoint_schema VARCHAR(80) NOT NULL,
    checkpoint_json CLOB NOT NULL,
    checkpoint_hash CHAR(64) NOT NULL,
    series_revision BIGINT NOT NULL CHECK (series_revision >= 0),
    series_status VARCHAR(40) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (binding_hash)
        REFERENCES career_competition_series_binding(binding_hash)
);

CREATE TABLE career_competition_job (
    job_id VARCHAR(80) PRIMARY KEY,
    binding_hash CHAR(64) NOT NULL,
    client_command_id VARCHAR(80) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 0),
    lease_token CHAR(64),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    completion_receipt_hash CHAR(64),
    failure_code VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (binding_hash, client_command_id),
    FOREIGN KEY (binding_hash)
        REFERENCES career_competition_series_binding(binding_hash)
);

CREATE TABLE career_competition_command (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    client_command_id VARCHAR(80) NOT NULL,
    command_type VARCHAR(40) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    binding_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, client_command_id),
    FOREIGN KEY (career_id) REFERENCES career_save(career_id),
    FOREIGN KEY (binding_hash)
        REFERENCES career_competition_series_binding(binding_hash)
);

CREATE TABLE career_competition_completion_receipt (
    receipt_hash CHAR(64) PRIMARY KEY,
    binding_hash CHAR(64) NOT NULL UNIQUE,
    receipt_schema VARCHAR(80) NOT NULL,
    receipt_canonical CLOB NOT NULL,
    receipt_json CLOB NOT NULL,
    first_score INTEGER NOT NULL CHECK (first_score >= 0),
    second_score INTEGER NOT NULL CHECK (second_score >= 0),
    winner_team_code VARCHAR(16) NOT NULL,
    loser_team_code VARCHAR(16) NOT NULL,
    total_duration_seconds INTEGER NOT NULL CHECK (total_duration_seconds > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (binding_hash)
        REFERENCES career_competition_series_binding(binding_hash)
);

CREATE TABLE career_competition_result_detail (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64) NOT NULL,
    binding_hash CHAR(64) NOT NULL,
    receipt_hash CHAR(64) NOT NULL,
    first_score INTEGER NOT NULL CHECK (first_score >= 0),
    second_score INTEGER NOT NULL CHECK (second_score >= 0),
    total_duration_seconds INTEGER NOT NULL CHECK (total_duration_seconds > 0),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, competition_id, match_id),
    UNIQUE (receipt_hash),
    FOREIGN KEY (career_id, calendar_season_year, competition_id, match_id)
        REFERENCES career_competition_fixture(
            career_id, calendar_season_year, competition_id, match_id),
    FOREIGN KEY (binding_hash)
        REFERENCES career_competition_series_binding(binding_hash),
    FOREIGN KEY (receipt_hash)
        REFERENCES career_competition_completion_receipt(receipt_hash)
);

CREATE TABLE career_lck_cup_standing (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    group_id VARCHAR(16) NOT NULL,
    group_rank INTEGER NOT NULL CHECK (group_rank BETWEEN 1 AND 5),
    team_code VARCHAR(16) NOT NULL,
    match_wins INTEGER NOT NULL CHECK (match_wins >= 0),
    match_losses INTEGER NOT NULL CHECK (match_losses >= 0),
    game_wins INTEGER NOT NULL CHECK (game_wins >= 0),
    game_losses INTEGER NOT NULL CHECK (game_losses >= 0),
    strength_of_victory INTEGER NOT NULL CHECK (strength_of_victory >= 0),
    win_time_seconds INTEGER NOT NULL CHECK (win_time_seconds >= 0),
    tie_break_trace VARCHAR(512) NOT NULL,
    standings_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, group_id, group_rank),
    UNIQUE (career_id, calendar_season_year, team_code),
    FOREIGN KEY (career_id, calendar_season_year)
        REFERENCES career_competition_cycle(career_id, calendar_season_year)
);

CREATE TABLE career_competition_opponent_choice (
    choice_hash CHAR(64) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    match_id VARCHAR(64) NOT NULL,
    choice_owner_team_code VARCHAR(16) NOT NULL,
    eligible_seed_order VARCHAR(512) NOT NULL,
    chosen_team_code VARCHAR(16) NOT NULL,
    policy_id VARCHAR(128) NOT NULL,
    policy_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (career_id, calendar_season_year, competition_id, match_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id, match_id)
        REFERENCES career_competition_fixture(
            career_id, calendar_season_year, competition_id, match_id)
);

CREATE INDEX idx_career_competition_job_status
    ON career_competition_job(lifecycle_status, created_at, job_id);
