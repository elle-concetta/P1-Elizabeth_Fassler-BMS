-- User-approved additive setup. Review and execute this file manually in MySQL.
-- CREATE TABLE implicitly commits: use a session without unrelated pending work.
-- It never drops/alters existing tables, creates users, grants privileges or inserts sample data.
-- Fully qualified target: never apply this to banking_test or an unrelated catalog.
-- No IF NOT EXISTS: an existing table must be inspected, not silently accepted.
CREATE TABLE banking_db.security_audit (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    customer_id BIGINT NOT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at TIMESTAMP NOT NULL,
        PRIMARY KEY (event_id),
    KEY idx_security_customer_time (customer_id, created_at),
        CONSTRAINT fk_security_customer FOREIGN KEY (customer_id)
        REFERENCES banking_db.customers(customer_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
        CONSTRAINT chk_security_event CHECK (event_type = 'PASSWORD_CHANGE'),
        CONSTRAINT chk_security_result CHECK (
            (outcome = 'SUCCESS' AND failure_reason IS NULL)
                OR (outcome = 'FAILURE' AND failure_reason IS NOT NULL
                AND failure_reason = 'INVALID_CURRENT_PASSWORD')
                OR (outcome = 'LOCKED' AND failure_reason IS NOT NULL
                AND failure_reason IN ('ACCOUNT_UNAVAILABLE', 'TOO_MANY_ATTEMPTS'))
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
