-- A single current run per Career. Original command receipts survive replacement by a later run.
CREATE TABLE career_continuous_run (
    career_id VARCHAR(80) PRIMARY KEY REFERENCES career_save(career_id),
    run_id VARCHAR(80) NOT NULL UNIQUE,
    lifecycle_status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    state_json CLOB NOT NULL,
    state_hash CHAR(64) NOT NULL,
    lease_owner VARCHAR(80),
    lease_fence BIGINT NOT NULL DEFAULT 0,
    lease_until TIMESTAMP WITH TIME ZONE,
    next_wake TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE career_continuous_command (
    client_command_id VARCHAR(80) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL REFERENCES career_save(career_id),
    payload_json CLOB NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    receipt_json CLOB NOT NULL,
    receipt_hash CHAR(64) NOT NULL
);

-- Existing baseline jobs retain their V1 frozen-input canonical form. New jobs bind V2 explicitly.
ALTER TABLE league_job ADD COLUMN match_policy_id VARCHAR(80);
