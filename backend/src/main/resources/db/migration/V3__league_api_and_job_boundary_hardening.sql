CREATE TABLE league_process_incarnation (
    incarnation_id VARCHAR(96) PRIMARY KEY,
    lifecycle_status VARCHAR(24) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE league_job_scheduler_lock (
    lock_name VARCHAR(64) PRIMARY KEY,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO league_job_scheduler_lock(lock_name, revision, updated_at)
VALUES ('GLOBAL_FIXTURE_LEASES', 0, CURRENT_TIMESTAMP);

INSERT INTO league_job_scheduler_lock(lock_name, revision, updated_at)
VALUES ('API_COMMANDS', 0, CURRENT_TIMESTAMP);

ALTER TABLE league_job ADD COLUMN lease_incarnation_id VARCHAR(96);

CREATE INDEX idx_league_job_incarnation
    ON league_job(lease_incarnation_id, lifecycle_status);

CREATE TABLE league_api_command (
    client_command_id VARCHAR(160) PRIMARY KEY,
    command_type VARCHAR(64) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    league_id VARCHAR(80),
    season_id VARCHAR(80),
    fixture_id VARCHAR(80),
    lifecycle_status VARCHAR(24) NOT NULL,
    http_status INTEGER,
    response_json CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CHECK (lifecycle_status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CHECK ((lifecycle_status = 'COMPLETED' AND http_status IS NOT NULL
              AND response_json IS NOT NULL AND completed_at IS NOT NULL)
        OR lifecycle_status <> 'COMPLETED')
);

CREATE INDEX idx_league_api_command_scope
    ON league_api_command(season_id, fixture_id, command_type);

UPDATE league_schema_version
SET schema_token = 'AI_LEAGUE_API_AND_JOB_BOUNDARY_V1',
    installed_at = CURRENT_TIMESTAMP
WHERE schema_name = 'AI_LEAGUE_V1';
