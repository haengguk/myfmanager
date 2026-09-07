-- Authoring state is copied only when creating a new Career. Existing directories remain immutable.
CREATE TABLE player_data_revision (id INTEGER PRIMARY KEY, revision BIGINT NOT NULL);
INSERT INTO player_data_revision VALUES (1, 0);
CREATE TABLE player_data_override (
    player_id VARCHAR(128) PRIMARY KEY,
    ratings_json CLOB NOT NULL,
    potential_ability INTEGER,
    revision BIGINT NOT NULL,
    CHECK (potential_ability IS NULL OR potential_ability BETWEEN 1 AND 200)
);
CREATE TABLE player_data_command (
    command_id VARCHAR(36) PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    receipt_json CLOB NOT NULL
);
