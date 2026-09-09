-- Supervisor metadata; tombstones prevent delayed adds from reversing later removals.
CREATE TABLE career_scout_interest (
 career_id VARCHAR(80) NOT NULL, player_id VARCHAR(128) NOT NULL,
 revision BIGINT NOT NULL, selected BOOLEAN NOT NULL,
 PRIMARY KEY(career_id,player_id)
);
CREATE TABLE career_record_draft (
 record_id CHAR(64) NOT NULL REFERENCES career_record_series(record_id),
 game_number INTEGER NOT NULL, evidence_json CLOB NOT NULL, evidence_hash CHAR(64) NOT NULL,
 PRIMARY KEY(record_id,game_number)
);
