-- Existing market JSON/receipts and frozen Series stay intact. New observation starts at migration/startup.
CREATE TABLE career_appearance_binding (
    career_id VARCHAR(128) NOT NULL,
    fixture_identity VARCHAR(512) NOT NULL,
    snapshot_json CLOB NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    applied_receipt VARCHAR(128),
    PRIMARY KEY (career_id, fixture_identity),
    FOREIGN KEY (career_id) REFERENCES career_save(career_id)
);
CREATE TABLE career_opportunity_registration (
    career_id VARCHAR(128) NOT NULL,
    season_year INTEGER NOT NULL,
    competition_id VARCHAR(128) NOT NULL,
    registered_date DATE NOT NULL,
    PRIMARY KEY (career_id, season_year, competition_id),
    FOREIGN KEY (career_id) REFERENCES career_save(career_id)
);
