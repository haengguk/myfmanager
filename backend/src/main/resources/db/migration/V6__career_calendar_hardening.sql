ALTER TABLE career_calendar_advance_command
    ADD COLUMN request_mode VARCHAR(40);

ALTER TABLE career_calendar_advance_command
    ADD COLUMN request_expected_revision BIGINT
        CHECK (request_expected_revision >= 0);

ALTER TABLE career_calendar_advance_command
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE career_calendar_advance_command
SET updated_at = COALESCE(completed_at, created_at);

ALTER TABLE career_calendar_advance_command
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE career_calendar_advance_command
    ADD CONSTRAINT chk_career_calendar_advance_request_v2
    CHECK ((request_mode IS NULL AND request_expected_revision IS NULL)
        OR (request_mode IN ('ADVANCE_ONE_DAY', 'ADVANCE_TO_NEXT_EVENT')
            AND request_expected_revision IS NOT NULL));
