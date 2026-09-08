-- Archive the exact pre-KRW current projection. Frozen results and original command receipts stay untouched.
CREATE TABLE career_finance_transition (
    career_id VARCHAR(100) PRIMARY KEY,
    original_json TEXT NOT NULL,
    original_hash VARCHAR(64) NOT NULL,
    introduced_on DATE NOT NULL,
    policy_version VARCHAR(100) NOT NULL,
    reference_hash VARCHAR(64) NOT NULL
);
