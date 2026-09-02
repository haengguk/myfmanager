CREATE TABLE career_schema_version (
    schema_name VARCHAR(96) PRIMARY KEY,
    schema_token VARCHAR(128) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO career_schema_version(schema_name, schema_token, installed_at)
VALUES ('CAREER_MODE_V1', 'CAREER_MODE_FOUNDATION_V1', CURRENT_TIMESTAMP);

CREATE TABLE career_save (
    career_id VARCHAR(80) PRIMARY KEY,
    save_name VARCHAR(80) NOT NULL,
    manager_name VARCHAR(80) NOT NULL,
    managed_team_code VARCHAR(16) NOT NULL,
    start_game_date DATE NOT NULL,
    current_game_date DATE NOT NULL,
    league_id VARCHAR(80) NOT NULL,
    season_id VARCHAR(80) NOT NULL,
    career_root_seed BIGINT NOT NULL,
    seed_algorithm_id VARCHAR(128) NOT NULL,
    league_frozen_snapshot_hash CHAR(64) NOT NULL,
    league_product_decision_hash CHAR(64) NOT NULL,
    reference_catalog_version VARCHAR(128) NOT NULL,
    reference_catalog_hash CHAR(64) NOT NULL,
    career_binding_schema VARCHAR(96) NOT NULL,
    career_binding_hash CHAR(64) NOT NULL UNIQUE,
    career_schema VARCHAR(96) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (league_id, season_id),
    FOREIGN KEY (league_id, season_id)
        REFERENCES league_season(league_id, season_id),
    CHECK (lifecycle_status IN ('ACTIVE'))
);

CREATE INDEX idx_career_save_updated
    ON career_save(updated_at DESC, career_id);

CREATE TABLE career_create_command (
    client_command_id CHAR(36) PRIMARY KEY,
    command_schema VARCHAR(96) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    career_id VARCHAR(80) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (career_id) REFERENCES career_save(career_id)
);

CREATE TABLE career_operation_lock (
    lock_name VARCHAR(64) PRIMARY KEY,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO career_operation_lock(lock_name, revision, updated_at)
VALUES ('CREATE_COMMANDS', 0, CURRENT_TIMESTAMP);
