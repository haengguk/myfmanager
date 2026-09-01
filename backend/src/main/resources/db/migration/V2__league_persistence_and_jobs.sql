CREATE TABLE league_registry (
    league_id VARCHAR(80) PRIMARY KEY,
    lifecycle_status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE league_season (
    season_id VARCHAR(80) PRIMARY KEY,
    league_id VARCHAR(80) NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    lifecycle_revision BIGINT NOT NULL CHECK (lifecycle_revision >= 0),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    season_mode VARCHAR(32) NOT NULL,
    managed_team_code VARCHAR(16),
    managed_team_snapshot_hash CHAR(64),
    season_root_seed BIGINT NOT NULL,
    product_decision_hash CHAR(64) NOT NULL,
    schedule_identity CHAR(64) NOT NULL,
    frozen_snapshot_json CLOB NOT NULL,
    frozen_snapshot_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (league_id, season_id),
    FOREIGN KEY (league_id) REFERENCES league_registry(league_id)
);

CREATE TABLE league_round (
    season_id VARCHAR(80) NOT NULL,
    round_number INTEGER NOT NULL CHECK (round_number > 0),
    lifecycle_status VARCHAR(40) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    PRIMARY KEY (season_id, round_number),
    FOREIGN KEY (season_id) REFERENCES league_season(season_id)
);

CREATE TABLE league_fixture (
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    round_number INTEGER NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    lifecycle_status VARCHAR(48) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    bound_series_id VARCHAR(80) NOT NULL,
    first_team_code VARCHAR(16) NOT NULL,
    second_team_code VARCHAR(16) NOT NULL,
    game1_blue_team_code VARCHAR(16) NOT NULL,
    game1_red_team_code VARCHAR(16) NOT NULL,
    series_format VARCHAR(16) NOT NULL,
    fixture_root_seed BIGINT NOT NULL,
    seed_anchor_team_code VARCHAR(16) NOT NULL,
    completion_receipt_hash CHAR(64),
    failure_code VARCHAR(160),
    PRIMARY KEY (season_id, fixture_id),
    FOREIGN KEY (season_id, round_number)
        REFERENCES league_round(season_id, round_number),
    UNIQUE (season_id, bound_series_id)
);

CREATE INDEX idx_league_fixture_round_status
    ON league_fixture(season_id, round_number, lifecycle_status);

CREATE TABLE league_standing (
    season_id VARCHAR(80) NOT NULL,
    team_code VARCHAR(16) NOT NULL,
    series_wins INTEGER NOT NULL CHECK (series_wins >= 0),
    series_losses INTEGER NOT NULL CHECK (series_losses >= 0),
    game_wins INTEGER NOT NULL CHECK (game_wins >= 0),
    game_losses INTEGER NOT NULL CHECK (game_losses >= 0),
    PRIMARY KEY (season_id, team_code),
    FOREIGN KEY (season_id) REFERENCES league_season(season_id)
);

CREATE TABLE league_player_binding (
    binding_hash CHAR(64) PRIMARY KEY,
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    binding_schema VARCHAR(96) NOT NULL,
    binding_canonical CLOB NOT NULL,
    binding_json CLOB NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    lifecycle_status VARCHAR(48) NOT NULL,
    reason VARCHAR(160),
    completion_receipt_hash CHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (season_id, fixture_id),
    FOREIGN KEY (season_id, fixture_id)
        REFERENCES league_fixture(season_id, fixture_id)
);

CREATE TABLE league_player_binding_command (
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    command_id VARCHAR(160) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    binding_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (season_id, fixture_id, command_id),
    UNIQUE (command_id),
    FOREIGN KEY (binding_hash) REFERENCES league_player_binding(binding_hash)
);

CREATE TABLE league_player_series_checkpoint (
    binding_hash CHAR(64) PRIMARY KEY,
    series_id VARCHAR(80) NOT NULL UNIQUE,
    checkpoint_schema VARCHAR(96) NOT NULL,
    checkpoint_json CLOB NOT NULL,
    checkpoint_hash CHAR(64) NOT NULL,
    series_revision BIGINT NOT NULL CHECK (series_revision >= 0),
    series_status VARCHAR(40) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (binding_hash) REFERENCES league_player_binding(binding_hash)
);

CREATE TABLE league_job (
    job_id VARCHAR(80) PRIMARY KEY,
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    lifecycle_status VARCHAR(40) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 0 AND attempt_number <= 2),
    fencing_number BIGINT NOT NULL CHECK (fencing_number >= 0),
    lease_token VARCHAR(96),
    lease_owner VARCHAR(160),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    frozen_input_hash CHAR(64) NOT NULL,
    failure_class VARCHAR(32),
    failure_code VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (season_id, fixture_id),
    FOREIGN KEY (season_id, fixture_id)
        REFERENCES league_fixture(season_id, fixture_id)
);

CREATE INDEX idx_league_job_status ON league_job(lifecycle_status, updated_at);

CREATE TABLE league_job_attempt (
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0 AND attempt_number <= 2),
    fencing_number BIGINT NOT NULL CHECK (fencing_number > 0),
    lifecycle_status VARCHAR(40) NOT NULL,
    owner_id VARCHAR(160) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    failure_class VARCHAR(32),
    failure_code VARCHAR(160),
    PRIMARY KEY (season_id, fixture_id, attempt_number),
    FOREIGN KEY (season_id, fixture_id)
        REFERENCES league_fixture(season_id, fixture_id)
);

CREATE TABLE league_completion_receipt (
    receipt_hash CHAR(64) PRIMARY KEY,
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    player_binding_hash CHAR(64),
    receipt_schema VARCHAR(96) NOT NULL,
    receipt_canonical CLOB NOT NULL,
    receipt_json CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (season_id, fixture_id),
    FOREIGN KEY (season_id, fixture_id)
        REFERENCES league_fixture(season_id, fixture_id)
);

CREATE TABLE league_outbox (
    event_id VARCHAR(80) PRIMARY KEY,
    event_schema VARCHAR(96) NOT NULL,
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    player_binding_hash CHAR(64),
    receipt_hash CHAR(64) NOT NULL UNIQUE,
    lifecycle_status VARCHAR(32) NOT NULL,
    delivery_attempts INTEGER NOT NULL CHECK (delivery_attempts >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (receipt_hash) REFERENCES league_completion_receipt(receipt_hash),
    FOREIGN KEY (season_id, fixture_id)
        REFERENCES league_fixture(season_id, fixture_id)
);

CREATE INDEX idx_league_outbox_status ON league_outbox(lifecycle_status, created_at);

CREATE TABLE league_standings_application (
    season_id VARCHAR(80) NOT NULL,
    fixture_id VARCHAR(80) NOT NULL,
    receipt_hash CHAR(64) NOT NULL,
    applied_season_revision BIGINT NOT NULL CHECK (applied_season_revision > 0),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (season_id, fixture_id, receipt_hash),
    UNIQUE (season_id, fixture_id),
    UNIQUE (receipt_hash),
    FOREIGN KEY (receipt_hash) REFERENCES league_completion_receipt(receipt_hash)
);

UPDATE league_schema_version
SET schema_token = 'AI_LEAGUE_PERSISTENCE_AND_JOBS_V1',
    installed_at = CURRENT_TIMESTAMP
WHERE schema_name = 'AI_LEAGUE_V1';
