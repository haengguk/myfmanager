CREATE TABLE career_calendar_state (
    career_id VARCHAR(80) PRIMARY KEY,
    calendar_schema VARCHAR(96) NOT NULL,
    template_version VARCHAR(128) NOT NULL,
    template_hash CHAR(64) NOT NULL,
    projection_policy VARCHAR(128) NOT NULL,
    anchor_algorithm VARCHAR(128) NOT NULL,
    fixture_allocation_policy VARCHAR(128) NOT NULL,
    active_calendar_season_year INTEGER NOT NULL CHECK (active_calendar_season_year >= 2026),
    current_game_date DATE NOT NULL,
    event_cursor INTEGER NOT NULL CHECK (event_cursor >= 0),
    calendar_revision BIGINT NOT NULL CHECK (calendar_revision >= 0),
    calendar_state_hash CHAR(64) NOT NULL,
    last_processed_event_id VARCHAR(96),
    last_processed_date DATE,
    lifecycle_status VARCHAR(40) NOT NULL,
    blocking_reason VARCHAR(96),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (career_id) REFERENCES career_save(career_id),
    CHECK (lifecycle_status IN (
        'MIGRATION_PENDING', 'ACTIVE', 'MIGRATION_REQUIRED',
        'SEASON_ROLLOVER_REQUIRED'))
);

CREATE TABLE career_calendar_advance_command (
    client_command_id CHAR(36) PRIMARY KEY,
    career_id VARCHAR(80) NOT NULL,
    command_schema VARCHAR(96) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    command_status VARCHAR(24) NOT NULL,
    result_active_calendar_season_year INTEGER,
    result_current_game_date DATE,
    result_event_cursor INTEGER,
    result_calendar_revision BIGINT CHECK (result_calendar_revision >= 0),
    result_state_hash CHAR(64),
    result_last_processed_event_id VARCHAR(96),
    result_last_processed_date DATE,
    result_lifecycle_status VARCHAR(40),
    result_blocking_reason VARCHAR(96),
    http_status INTEGER,
    stop_reason VARCHAR(96),
    background_required BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (career_id) REFERENCES career_calendar_state(career_id),
    CHECK (command_status IN ('PENDING', 'COMPLETED')),
    CHECK ((command_status = 'PENDING' AND completed_at IS NULL)
        OR (command_status = 'COMPLETED' AND completed_at IS NOT NULL)),
    CHECK (command_status <> 'COMPLETED'
        OR (result_active_calendar_season_year IS NOT NULL
            AND result_current_game_date IS NOT NULL
            AND result_event_cursor IS NOT NULL
            AND result_calendar_revision IS NOT NULL
            AND result_state_hash IS NOT NULL
            AND result_lifecycle_status IS NOT NULL
            AND http_status IS NOT NULL))
);

CREATE INDEX idx_career_calendar_advance_scope
    ON career_calendar_advance_command(career_id, command_status, created_at);

CREATE TABLE career_calendar_operation_lock (
    lock_name VARCHAR(64) PRIMARY KEY,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO career_calendar_operation_lock(lock_name, revision, updated_at)
VALUES ('ADVANCE_COMMANDS', 0, CURRENT_TIMESTAMP);

INSERT INTO career_calendar_state(
    career_id, calendar_schema, template_version, template_hash,
    projection_policy, anchor_algorithm, fixture_allocation_policy,
    active_calendar_season_year, current_game_date, event_cursor,
    calendar_revision, calendar_state_hash, last_processed_event_id,
    last_processed_date, lifecycle_status, blocking_reason, created_at, updated_at)
SELECT career_id,
       'CAREER_CALENDAR_STATE_V1',
       'lck-career-calendar-reference-2026-v1',
       '34a837ad384c49518093cc045d054540b889292002f9b71c01d53c20e1382e38',
       'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1',
       'FIRST_FULL_CYCLE_AFTER_CURRENT_DATE_V1',
       'ROUND_LINEAR_INCLUSIVE_WINDOW_ONE_SLOT_PER_ROUND_V1',
       YEAR(current_game_date) + 1,
       current_game_date,
       0,
       0,
       '0000000000000000000000000000000000000000000000000000000000000000',
       NULL,
       NULL,
       'MIGRATION_PENDING',
       'CAREER_CALENDAR_MIGRATION_REQUIRED',
       created_at,
       updated_at
FROM career_save;
