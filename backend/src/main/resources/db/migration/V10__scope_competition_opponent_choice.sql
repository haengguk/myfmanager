-- choice_hash certifies selection content; fixture scope identifies its stored use.
-- No foreign keys reference choice_hash. Preserve its bytes and all existing rows.
ALTER TABLE career_competition_opponent_choice DROP PRIMARY KEY;
ALTER TABLE career_competition_opponent_choice
    ADD PRIMARY KEY (career_id, calendar_season_year, competition_id, match_id);
