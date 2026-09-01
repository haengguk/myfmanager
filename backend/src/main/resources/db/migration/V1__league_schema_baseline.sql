CREATE TABLE league_schema_version (
    schema_name VARCHAR(96) PRIMARY KEY,
    schema_token VARCHAR(128) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO league_schema_version(schema_name, schema_token, installed_at)
VALUES ('AI_LEAGUE_V1', 'AI_LEAGUE_RELATIONAL_BASELINE_V1', CURRENT_TIMESTAMP);
