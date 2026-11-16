-- Secure Banking CLI: MySQL 8.4+ initial schema.
-- Based on section 17.1 of the supplied project document.
-- Extended with the supporting Database Design document; see README.md#design-decisions.
-- Nine tables: seven baseline tables, daily balances, and ledger-change audit.
--
-- Run once against an EMPTY database selected by the caller:
-- CREATE DATABASE banking_db CHARACTER SET utf8mb4
-- COLLATE utf8mb4_0900_ai_ci;
-- USE banking_db;
-- SOURCE sql/mysql/schema.sql;
--
-- No CREATE DATABASE or USE statement is executed here so the same schema can
-- be applied to a separate banking_test database without redirecting to live data.
-- This is NOT a migration: inspect existing tables/triggers before proceeding.
-- There are no DROP statements, sample passwords, seed rows, or money operations.
-- MySQL DDL can commit implicitly; a failure may leave a partially applied schema.
-- Use strict SQL mode. Application input validation remains required.

CREATE TABLE customers (
    customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_code VARCHAR(30) NOT NULL UNIQUE,
    first_name VARCHAR(60) NOT NULL,
    middle_initial CHAR(1),
    last_name VARCHAR(60) NOT NULL,
    date_of_birth DATE,
    address VARCHAR(255),
    email VARCHAR(254) NOT NULL UNIQUE,
    phone VARCHAR(30),
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status ENUM('ACTIVE', 'LOCKED', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_customer_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'CLOSED')),
    CONSTRAINT chk_failed_count CHECK (failed_login_count >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,
    -- LOAN balance is outstanding principal, not spendable cash.
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    -- Savings annual percentage rate. Deposit/loan rates remain authoritative
    -- in deposit_details / loan_details; leave this rate at zero for those types.
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    minimum_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    -- Explicit business opening date; Java supplies it when creating an account.
    opening_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_account_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id),

    CONSTRAINT chk_account_type
        CHECK (account_type IN ('SAVINGS', 'CURRENT', 'DEPOSIT', 'LOAN')),

    CONSTRAINT chk_account_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'MATURED', 'CLOSED')),

    CONSTRAINT chk_account_balance CHECK (balance >= 0),

    CONSTRAINT chk_account_interest_rate
        CHECK (interest_rate BETWEEN 0 AND 100),

    CONSTRAINT chk_account_minimum_balance CHECK (minimum_balance >= 0),

    INDEX idx_accounts_customer (customer_id),

    INDEX idx_accounts_type (account_type)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Domain/service validation must also ensure this account has type DEPOSIT.
CREATE TABLE deposit_details (
    account_id BIGINT PRIMARY KEY,
    principal DECIMAL(19,2) NOT NULL,
    term_months INT NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    maturity_date DATE NOT NULL,

    CONSTRAINT fk_dep_account
        FOREIGN KEY (account_id) REFERENCES accounts(account_id),

    CONSTRAINT chk_dep_term CHECK (term_months > 0 AND principal > 0),

    CONSTRAINT chk_deposit_interest_rate CHECK (interest_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Domain/service validation must also ensure this account has type LOAN.
CREATE TABLE loan_details (
    account_id BIGINT PRIMARY KEY,
    principal_amount DECIMAL(19,2) NOT NULL,
    annual_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tenure_months INT NOT NULL,
    start_date DATE NOT NULL,

    CONSTRAINT fk_loan_account
        FOREIGN KEY (account_id) REFERENCES accounts(account_id),

    CONSTRAINT chk_loan_vals
        CHECK (principal_amount > 0 AND tenure_months > 0),

    CONSTRAINT chk_loan_annual_rate CHECK (annual_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Baseline application writes one SUCCESS ledger row per committed operation.
-- Other statuses are retained from the reference DDL, not implemented workflows.
-- Deposit funding/maturity settlement uses TRANSFER, not a MATURITY credit.
CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reference_number VARCHAR(40) NOT NULL UNIQUE,
    source_account_id BIGINT NULL,
    destination_account_id BIGINT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(255),
    -- Endpoint-specific snapshots avoid ambiguity on transfers.
    source_balance_before DECIMAL(19,2) NULL,
    dest_balance_before DECIMAL(19,2) NULL,
    source_balance_after DECIMAL(19,2) NULL,
    dest_balance_after DECIMAL(19,2) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_source
        FOREIGN KEY (source_account_id) REFERENCES accounts(account_id),

    CONSTRAINT fk_txn_dest
        FOREIGN KEY (destination_account_id) REFERENCES accounts(account_id),

    CONSTRAINT chk_txn_amount CHECK (amount > 0),

    CONSTRAINT chk_txn_type
        CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'LOAN_REPAYMENT'
        )),

    CONSTRAINT chk_txn_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')),

    CONSTRAINT chk_txn_source_balance_before
        CHECK (source_balance_before IS NULL OR source_balance_before >= 0),

    CONSTRAINT chk_txn_dest_balance_before
        CHECK (dest_balance_before IS NULL OR dest_balance_before >= 0),

    CONSTRAINT chk_txn_source_balance_after
        CHECK (source_balance_after IS NULL OR source_balance_after >= 0),

    CONSTRAINT chk_txn_dest_balance_after
        CHECK (dest_balance_after IS NULL OR dest_balance_after >= 0),

    CONSTRAINT chk_txn_shape CHECK (
        (transaction_type = 'DEPOSIT'
            AND source_account_id IS NULL
            AND destination_account_id IS NOT NULL)
            OR
        (transaction_type = 'WITHDRAWAL'
            AND source_account_id IS NOT NULL
            AND destination_account_id IS NULL)
            OR
        (transaction_type IN ('TRANSFER', 'LOAN_REPAYMENT')
            AND source_account_id IS NOT NULL
            AND destination_account_id IS NOT NULL
            AND source_account_id <> destination_account_id)
                ),
        INDEX idx_txn_src_time (source_account_id, created_at, transaction_id),
        INDEX idx_txn_dst_time (destination_account_id, created_at, transaction_id)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE login_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- Unknown attempted usernames have no associated customer.
    customer_id BIGINT NULL,
    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    -- Optional in a local CLI: do not invent network/device context.
                             ip_address VARCHAR(45) NULL,
                             device_info VARCHAR(255) NULL,
    -- Controlled safe reason only, never credentials or raw exceptions.
                             failure_reason VARCHAR(255) NULL,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_audit_customer
                                 FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                             CONSTRAINT chk_audit_outcome
                                 CHECK (outcome IN ('SUCCESS', 'FAILURE', 'LOCKED', 'TIMEOUT', 'LOGOUT')),
                             INDEX idx_login_time (created_at),
                             INDEX idx_login_outcome (outcome)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Write through a separate JDBC transaction AFTER primary commit/rollback.
-- No ledger FK: rejected/rolled-back attempts have no committed ledger row.
-- Safe failure codes only; never raw SQL, passwords, or stack traces.
CREATE TABLE transaction_audit (
                                   audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                   customer_id BIGINT NOT NULL,
                                   reference_number VARCHAR(40) NULL,
                                   event_type VARCHAR(30) NOT NULL,
                                   outcome VARCHAR(20) NOT NULL,
                                   failure_code VARCHAR(40) NULL,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   CONSTRAINT fk_tauditcust
                                       FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                                   CONSTRAINT chk_transaction_audit_outcome
                                       CHECK (outcome IN ('SUCCESS', 'REJECTED', 'FAILED', 'ROLLED_BACK')),
                                   INDEX idx_taudit_customer (customer_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- End-of-day history for future monthly average balance / interest features.
-- A complete calendar of closing balances is needed, not just transaction days.
-- Java or an authorized end-of-day job must populate this table; this DDL does
-- not schedule snapshots or calculate/credit interest.
CREATE TABLE account_daily_balance (
                                       snapshot_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                       account_id BIGINT NOT NULL,
                                       balance_date DATE NOT NULL,
                                       closing_balance DECIMAL(19,2) NOT NULL,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       CONSTRAINT fk_daily_balance_account
                                           FOREIGN KEY (account_id) REFERENCES accounts(account_id),
                                       CONSTRAINT chk_closing_balance CHECK (closing_balance >= 0),
                                       CONSTRAINT uk_account_balance_date UNIQUE (account_id, balance_date),
                                       INDEX idx_balance_date (balance_date)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Row-change audit from the supporting guide, separate from financial attempts.
-- Trigger rows share the ledger transaction and therefore roll back with it.
-- They do NOT replace the independently committed transaction_audit.
CREATE TABLE transaction_change_audit (
                                          audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                          transaction_id BIGINT NOT NULL,
                                          operation VARCHAR(10) NOT NULL,
                                          old_amount DECIMAL(19,2) NULL,
                                          new_amount DECIMAL(19,2) NULL,
                                          old_status VARCHAR(20) NULL,
                                          new_status VARCHAR(20) NULL,
    -- CURRENT_USER() identifies the database principal, NOT the CLI customer.
    -- The authenticated customer belongs in transaction_audit.customer_id.
                                          changed_by VARCHAR(288) NOT NULL,
                                          audit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          CONSTRAINT fk_change_audit_transaction
                                              FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),
                                          CONSTRAINT chk_change_audit_operation
                                              CHECK (operation IN ('INSERT', 'UPDATE')),
                                          INDEX idx_change_audit_transaction (transaction_id),
                                          INDEX idx_change_audit_time (audit_time)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Optional customer extension, not a financial-processing trigger.
-- CURRENT_DATE uses the session time zone; Java must use the same date policy.
DELIMITER //

CREATE TRIGGER trg_check_dob_insert
    BEFORE INSERT ON customers
    FOR EACH ROW
BEGIN
    IF NEW.date_of_birth IS NOT NULL
       AND NEW.date_of_birth >= CURRENT_DATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Date of birth must be in the past.';
END IF;
END //

CREATE TRIGGER trg_check_dob_update
    BEFORE UPDATE ON customers
    FOR EACH ROW
BEGIN
    IF NEW.date_of_birth IS NOT NULL
       AND NEW.date_of_birth >= CURRENT_DATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Date of birth must be in the past.';
END IF;
END //

-- Passive row-change auditing only: no balance changes or commit/rollback here.
CREATE TRIGGER trg_transaction_after_insert
    AFTER INSERT ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id, operation, new_amount, new_status, changed_by
    )
    VALUES (
               NEW.transaction_id, 'INSERT', NEW.amount, NEW.status, CURRENT_USER()
           );
END //

-- Diagnostic coverage for privileged changes, not an application edit workflow.
-- Baseline Java must still treat committed ledger entries as append-only.
CREATE TRIGGER trg_transaction_after_update
    AFTER UPDATE ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id, operation, old_amount, new_amount,
        old_status, new_status, changed_by
    )
    VALUES (
               NEW.transaction_id, 'UPDATE', OLD.amount, NEW.amount,
               OLD.status, NEW.status, CURRENT_USER()
           );
END //

DELIMITER ;

-- No AFTER DELETE audit trigger: the retained change-audit foreign key blocks
-- deletion of audited ledger rows. Do not delete audit history to bypass it.
-- Required Java/JDBC behavior, NOT implemented by this schema:
-- ownership/type/status checks, explicit READ COMMITTED, ascending account-ID
-- FOR UPDATE locks, atomic balance+ledger commit, bounded rollback-safe retries,
-- independent audit writes, and uncertain-commit reconciliation.
-- Java must enforce savings minimums consistently on eligible outgoing money
-- operations (including transfers), ensure deposit maturity_date >= opening_date
-- across the two tables, and populate both before/after endpoint snapshots.
-- No interest engine, scheduled snapshots, financial stored procedures, or seeds.
