ALTER TABLE career_competition_cycle
    ADD COLUMN hash_algorithm VARCHAR(96) NOT NULL
        DEFAULT 'CAREER_COMPETITION_CYCLE_SHA256_CANONICAL_V1';

ALTER TABLE career_competition_cycle
    ADD COLUMN season_ordinal INTEGER NOT NULL DEFAULT 1
        CHECK (season_ordinal >= 1);

ALTER TABLE career_competition_cycle
    ADD COLUMN initialization_policy_id VARCHAR(128);

ALTER TABLE career_competition_cycle
    ADD COLUMN initialization_input_hash CHAR(64);

ALTER TABLE career_competition_instance
    ADD COLUMN hash_algorithm VARCHAR(96) NOT NULL
        DEFAULT 'CAREER_COMPETITION_INSTANCE_SHA256_CANONICAL_V1';

ALTER TABLE career_competition_instance
    ADD COLUMN materialization_policy_id VARCHAR(128);

ALTER TABLE career_competition_instance
    ADD COLUMN materialization_receipt_hash CHAR(64);

ALTER TABLE career_competition_fixture
    ADD COLUMN stage_id VARCHAR(64) NOT NULL DEFAULT 'UNSPECIFIED';

ALTER TABLE career_competition_fixture
    ADD COLUMN match_order INTEGER NOT NULL DEFAULT 0
        CHECK (match_order >= 0);

ALTER TABLE career_competition_fixture
    ADD COLUMN group_id VARCHAR(64);

ALTER TABLE career_competition_fixture
    ADD COLUMN group_point_value INTEGER
        CHECK (group_point_value IS NULL OR group_point_value >= 0);

ALTER TABLE career_competition_fixture
    ADD COLUMN selection_right_owner VARCHAR(96);

ALTER TABLE career_competition_fixture
    ADD COLUMN opponent_choice_policy VARCHAR(128);

ALTER TABLE career_competition_fixture
    ADD COLUMN side_selection_policy VARCHAR(128);

CREATE INDEX idx_career_competition_fixture_order
    ON career_competition_fixture(
        career_id, calendar_season_year, competition_id, match_order, match_id);
