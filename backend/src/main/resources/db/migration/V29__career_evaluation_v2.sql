-- Preserve every existing season. Adoption happens only at a new-season write boundary.
CREATE TABLE career_evaluation_policy (
 career_id VARCHAR(80) NOT NULL, season_year INTEGER NOT NULL, evaluation_version VARCHAR(40) NOT NULL,
 PRIMARY KEY(career_id,season_year)
);
INSERT INTO career_evaluation_policy SELECT career_id,season_year,'CAREER_PERFORMANCE_V1' FROM career_season;
CREATE TABLE career_game_team_play (
 series_id VARCHAR(80) NOT NULL, game_number INTEGER NOT NULL, output_hash CHAR(64) NOT NULL,
 evidence_json CLOB NOT NULL, evidence_hash CHAR(64) NOT NULL,
 PRIMARY KEY(series_id,game_number)
);
ALTER TABLE career_record_player ADD COLUMN rating_version VARCHAR(40) DEFAULT 'CAREER_PERFORMANCE_V1' NOT NULL;
