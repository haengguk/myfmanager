CREATE TABLE career_international_state (
    career_id VARCHAR(80) NOT NULL,
    calendar_season_year INTEGER NOT NULL,
    competition_id VARCHAR(64) NOT NULL,
    state_json CLOB NOT NULL,
    state_hash CHAR(64) NOT NULL,
    PRIMARY KEY (career_id, calendar_season_year, competition_id),
    FOREIGN KEY (career_id, calendar_season_year, competition_id)
        REFERENCES career_competition_instance(career_id, calendar_season_year, competition_id)
);
