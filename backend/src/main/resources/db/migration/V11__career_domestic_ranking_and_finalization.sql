CREATE TABLE career_domestic_ranking_decision (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    decision_id VARCHAR(64) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    decision_json CLOB NOT NULL,
    decision_hash CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, competition_id, decision_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id)
        REFERENCES career_competition_instance(career_id, calendar_season_year, competition_id)
);

ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN rule_version VARCHAR(128);
ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN policy_version VARCHAR(128);
ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN result_evidence_hash CHAR(64);
ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN champion_team_code VARCHAR(16);
ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN runner_up_team_code VARCHAR(16);
ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN record_scope VARCHAR(80);
ALTER TABLE career_lck_final_ranking_snapshot ADD COLUMN authority_hash CHAR(64);

ALTER TABLE career_lck_cup_standing ADD COLUMN strength_twice INTEGER;
ALTER TABLE career_lck_cup_standing ADD COLUMN winning_game_seconds BIGINT;
ALTER TABLE career_lck_cup_standing ADD COLUMN winning_game_count INTEGER;
