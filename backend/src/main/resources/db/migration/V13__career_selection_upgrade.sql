CREATE TABLE career_international_selection_archive (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    original_state_json CLOB NOT NULL,
    original_state_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, competition_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id)
        REFERENCES career_international_state(career_id, calendar_season_year, competition_id)
);
