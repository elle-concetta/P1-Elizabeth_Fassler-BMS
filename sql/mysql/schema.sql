-- ============================================================
-- Secure Banking CLI: MySQL Schema
--
-- Nine tables and four triggers.
--
-- Financial workflows and transaction boundaries remain
-- in Java services using JDBC.
-- ============================================================


-- ============================================================
-- 1. CUSTOMERS
-- Customer details, authentication, and login lockout state.
-- ============================================================

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

    -- Store a secure password hash, never plaintext
    -- or a reversibly encrypted password.

    password_hash VARCHAR(255) NOT NULL,

    status ENUM('ACTIVE', 'LOCKED', 'CLOSED')
        NOT NULL DEFAULT 'ACTIVE',

    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_customer_status
        CHECK (
        status IN ('ACTIVE', 'LOCKED', 'CLOSED')
    ),

    CONSTRAINT chk_failed_count
        CHECK (failed_login_count >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 2. ACCOUNTS
-- Each account belongs to one customer.
--
-- Loan balance represents outstanding principal owed,
-- not money available for spending.
--
-- accounts.interest_rate stores the Savings annual percentage
-- rate. Deposit and Loan rates remain authoritative in their
-- respective detail tables.
-- ============================================================

CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,

    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,

    -- For a LOAN, balance represents outstanding principal owed.
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,

    -- Savings annual percentage rate.
    -- Deposit and Loan rates remain in their detail tables.
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,

    minimum_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    opening_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_account_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(customer_id),

    CONSTRAINT chk_account_type
        CHECK (
        account_type IN (
        'SAVINGS',
        'CURRENT',
        'DEPOSIT',
        'LOAN'
    )
),

    CONSTRAINT chk_account_status
        CHECK (
        status IN (
        'ACTIVE',
        'FROZEN',
        'MATURED',
        'CLOSED'
    )
),

    CONSTRAINT chk_account_balance
        CHECK (balance >= 0),

    CONSTRAINT chk_account_interest_rate
        CHECK (interest_rate BETWEEN 0 AND 100),

    CONSTRAINT chk_account_minimum_balance
        CHECK (minimum_balance >= 0),

    INDEX idx_accounts_customer (customer_id),
    INDEX idx_accounts_type (account_type)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 3. DEPOSIT DETAILS
-- Additional information for term-deposit accounts.
--
-- Java must verify:
--   - The associated account is a DEPOSIT account.
--   - Maturity date is consistent with the opening date
--     and the selected term.
--
-- Maturity date is stored here.
-- ============================================================

CREATE TABLE deposit_details (
    account_id BIGINT PRIMARY KEY,

    principal DECIMAL(19,2) NOT NULL,
    term_months INT NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    maturity_date DATE NOT NULL,

    CONSTRAINT fk_dep_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_dep_term
        CHECK (
        term_months > 0
        AND principal > 0
    ),

    CONSTRAINT chk_deposit_interest_rate
        CHECK (interest_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 4. LOAN DETAILS
-- Additional information for loan accounts.
-- ============================================================

CREATE TABLE loan_details (
    account_id BIGINT PRIMARY KEY,

    principal_amount DECIMAL(19,2) NOT NULL,
    annual_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tenure_months INT NOT NULL,
    start_date DATE NOT NULL,

    CONSTRAINT fk_loan_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_loan_vals
        CHECK (
        principal_amount > 0
        AND tenure_months > 0
    ),

    CONSTRAINT chk_loan_annual_rate
        CHECK (annual_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 5. TRANSACTIONS
-- Financial ledger.
--
-- The baseline application writes one SUCCESS ledger row
-- for each committed financial operation.
--
-- Other statuses are retained from the original reference
-- schema, not additional implemented workflows.
--
-- Term-deposit funding and maturity settlement use TRANSFER.
--
-- Java enforces:
--   - Ownership and account eligibility.
--   - Money precision and configurable operation limits.
--   - Locking, atomic balance updates, and ledger insertion.
--   - Correct before/after balance snapshots.
-- ============================================================

CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reference_number VARCHAR(40) NOT NULL UNIQUE,

    source_account_id BIGINT NULL,
    destination_account_id BIGINT NULL,

    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(255),

    -- Separate snapshots avoid ambiguity during transfers.
    source_balance_before DECIMAL(19,2) NULL,
    dest_balance_before DECIMAL(19,2) NULL,
    source_balance_after DECIMAL(19,2) NULL,
    dest_balance_after DECIMAL(19,2) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_source
        FOREIGN KEY (source_account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT fk_txn_dest
        FOREIGN KEY (destination_account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_txn_amount
        CHECK (amount > 0),

    CONSTRAINT chk_txn_type
        CHECK (
        transaction_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER',
        'LOAN_REPAYMENT'
    )
),

    CONSTRAINT chk_txn_status
        CHECK (
        status IN (
        'PENDING',
        'SUCCESS',
        'FAILED',
        'REVERSED'
    )
),

    CONSTRAINT chk_txn_source_balance_before
        CHECK (
        source_balance_before IS NULL
        OR source_balance_before >= 0
),

    CONSTRAINT chk_txn_dest_balance_before
        CHECK (
        dest_balance_before IS NULL
        OR dest_balance_before >= 0
),

    CONSTRAINT chk_txn_source_balance_after
        CHECK (
        source_balance_after IS NULL
        OR source_balance_after >= 0
),

    CONSTRAINT chk_txn_dest_balance_after
        CHECK (
        dest_balance_after IS NULL
        OR dest_balance_after >= 0
),

    CONSTRAINT chk_txn_shape
        CHECK (
    (
        transaction_type = 'DEPOSIT'
        AND source_account_id IS NULL
        AND destination_account_id IS NOT NULL
    )
        OR
    (
        transaction_type = 'WITHDRAWAL'
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NULL
    )
        OR
    (
        transaction_type IN (
        'TRANSFER',
        'LOAN_REPAYMENT'
    )
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NOT NULL
        AND source_account_id <> destination_account_id
    )
),

        INDEX idx_txn_src_time (
        source_account_id,
        created_at,
        transaction_id
    ),

        INDEX idx_txn_dst_time (
        destination_account_id,
        created_at,
        transaction_id
    )

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 6. LOGIN AUDIT
-- Authentication events.
--
-- customer_id may be NULL for unknown attempted usernames.
-- IP address and device information are optional in a CLI.
-- Do not invent metadata the application does not collect.
--
-- Never store passwords, hashes, session secrets,
-- or raw exception details here.
-- ============================================================

CREATE TABLE login_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NULL,

    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    ip_address VARCHAR(45) NULL,
    device_info VARCHAR(255) NULL,

    -- Controlled, safe reason only.

    failure_reason VARCHAR(255) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT chk_audit_outcome
        CHECK (
        outcome IN (
        'SUCCESS',
        'FAILURE',
        'LOCKED',
        'TIMEOUT',
        'LOGOUT'
    )
),

    INDEX idx_login_time (created_at),
    INDEX idx_login_outcome (outcome)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 7. TRANSACTION AUDIT
-- Records financial attempts and their outcomes.
--
-- Java writes these records in a separate short transaction
-- AFTER the financial transaction commits or rolls back.
--
-- There is intentionally no ledger foreign key:
-- rejected/rolled-back attempts may have no committed
-- transaction row.
-- ============================================================

CREATE TABLE transaction_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,

    reference_number VARCHAR(40) NULL,
    event_type VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    -- Safe codes, such as INSUFFICIENT_FUNDS or NOT_OWNER.

    failure_code VARCHAR(40) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tauditcust
        FOREIGN KEY (customer_id)
            REFERENCES customers(customer_id),

    CONSTRAINT chk_transaction_audit_outcome
        CHECK (
        outcome IN (
        'SUCCESS',
        'REJECTED',
        'FAILED',
        'ROLLED_BACK'
    )
),
    INDEX idx_taudit_customer (
        customer_id,
        created_at
    )

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 8. ACCOUNT DAILY BALANCE
-- One closing-balance snapshot per account per calendar date.
--
-- Supports future monthly average balance and interest work.
-- A complete set of daily balances is needed, not only
-- dates on which transactions occurred.
-- ============================================================

CREATE TABLE account_daily_balance (
    snapshot_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,

    balance_date DATE NOT NULL,
    closing_balance DECIMAL(19,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_daily_balance_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_closing_balance
        CHECK (closing_balance >= 0),

    CONSTRAINT uk_account_balance_date
        UNIQUE (account_id, balance_date),

    INDEX idx_balance_date (balance_date)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 9. TRANSACTION CHANGE AUDIT
-- Records ledger-row INSERT and UPDATE changes.
--
-- Separate from financial-attempt auditing.
--
-- Populated by the triggers below. These audit rows share
-- the ledger transaction and roll back with it.
-- An audit insert failure also fails the triggering write.
-- ============================================================

CREATE TABLE transaction_change_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id BIGINT NOT NULL,

    operation VARCHAR(10) NOT NULL,

    old_amount DECIMAL(19,2) NULL,
    new_amount DECIMAL(19,2) NULL,

    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NULL,

    -- Database principal, not authenticated CLI customer.
    -- The CLI customer is recorded in transaction_audit.

    changed_by VARCHAR(288) NOT NULL,

    audit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_change_audit_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES transactions(transaction_id),

    CONSTRAINT chk_change_audit_operation
        CHECK (
        operation IN ('INSERT', 'UPDATE')
),

    INDEX idx_change_audit_transaction (transaction_id),
    INDEX idx_change_audit_time (audit_time)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- TRIGGERS
--
-- DELIMITER is a MySQL client command understood by
-- MySQL Workbench and the mysql command-line client.
-- ============================================================

DELIMITER //

-- ============================================================
-- A. VALIDATE DOB BEFORE INSERT
-- NULL/past dates allowed; today/future dates rejected.
-- CURRENT_DATE() uses the database session's time zone.
-- Apply the same date policy in Java.
-- ============================================================

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

-- ============================================================
-- B. VALIDATE DOB BEFORE UPDATE
-- ============================================================

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

-- ============================================================
-- C. RECORD LEDGER INSERT
-- Passive auditing only; does not change account balances.
-- ============================================================

CREATE TRIGGER trg_transaction_after_insert
    AFTER INSERT ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id,
        operation,
        new_amount,
        new_status,
        changed_by
    )
    VALUES (
               NEW.transaction_id,
               'INSERT',
               NEW.amount,
               NEW.status,
               CURRENT_USER()
           );
END //


-- ============================================================
-- D. RECORD LEDGER UPDATE
-- Diagnostic coverage for privileged changes.
--
-- The baseline Java application must still treat committed
-- ledger records as append-only.
-- ============================================================

CREATE TRIGGER trg_transaction_after_update
    AFTER UPDATE ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id,
        operation,
        old_amount,
        new_amount,
        old_status,
        new_status,
        changed_by
    )
    VALUES (
               NEW.transaction_id,
               'UPDATE',
               OLD.amount,
               NEW.amount,
               OLD.status,
               NEW.status,
               CURRENT_USER()
           );
END //

DELIMITER ;


-- ============================================================
-- IMPLEMENTATION NOTES
--
-- Retained change-audit.
-- rows reference the ledger and prevent deletion of audited
-- transaction rows.
--
-- Required Java/JDBC behavior remains:
--   - Customer ownership and account eligibility checks.
--   - Explicit READ COMMITTED isolation.
--   - Ascending account-ID row-lock ordering.
--   - Atomic balance updates and ledger insertion.
--   - Bounded, rollback-safe retries.
--   - Independent financial-attempt audit writes.
--   - Uncertain-commit reconciliation.
--   - Consistent minimum-balance checks on eligible outgoing
--     operations, including transfers.
--   - Deposit maturity validation against account opening date.
--   - Correct endpoint before/after balance snapshots.
--
-- Stored interest functions, interest crediting, scheduled
-- snapshots, and financial stored procedures are not included.
-- ============================================================-- ============================================================
-- Secure Banking CLI: MySQL Schema
--
-- Nine tables and four triggers.
--
-- Financial workflows and transaction boundaries remain
-- in Java services using JDBC.
-- ============================================================


-- ============================================================
-- 1. CUSTOMERS
-- Customer details, authentication, and login lockout state.
-- ============================================================

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

    -- Store a secure password hash, never plaintext
    -- or a reversibly encrypted password.

    password_hash VARCHAR(255) NOT NULL,

    status ENUM('ACTIVE', 'LOCKED', 'CLOSED')
        NOT NULL DEFAULT 'ACTIVE',

    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_customer_status
        CHECK (
        status IN ('ACTIVE', 'LOCKED', 'CLOSED')
    ),

    CONSTRAINT chk_failed_count
        CHECK (failed_login_count >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 2. ACCOUNTS
-- Each account belongs to one customer.
--
-- Loan balance represents outstanding principal owed,
-- not money available for spending.
--
-- accounts.interest_rate stores the Savings annual percentage
-- rate. Deposit and Loan rates remain authoritative in their
-- respective detail tables.
-- ============================================================

CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,

    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,

    -- For a LOAN, balance represents outstanding principal owed.
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,

    -- Savings annual percentage rate.
    -- Deposit and Loan rates remain in their detail tables.
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,

    minimum_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    opening_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_account_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(customer_id),

    CONSTRAINT chk_account_type
        CHECK (
        account_type IN (
        'SAVINGS',
        'CURRENT',
        'DEPOSIT',
        'LOAN'
    )
),

    CONSTRAINT chk_account_status
        CHECK (
        status IN (
        'ACTIVE',
        'FROZEN',
        'MATURED',
        'CLOSED'
    )
),

    CONSTRAINT chk_account_balance
        CHECK (balance >= 0),

    CONSTRAINT chk_account_interest_rate
        CHECK (interest_rate BETWEEN 0 AND 100),

    CONSTRAINT chk_account_minimum_balance
        CHECK (minimum_balance >= 0),

    INDEX idx_accounts_customer (customer_id),
    INDEX idx_accounts_type (account_type)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 3. DEPOSIT DETAILS
-- Additional information for term-deposit accounts.
--
-- Java must verify:
--   - The associated account is a DEPOSIT account.
--   - Maturity date is consistent with the opening date
--     and the selected term.
--
-- Maturity date is stored here.
-- ============================================================

CREATE TABLE deposit_details (
    account_id BIGINT PRIMARY KEY,

    principal DECIMAL(19,2) NOT NULL,
    term_months INT NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    maturity_date DATE NOT NULL,

    CONSTRAINT fk_dep_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_dep_term
        CHECK (
        term_months > 0
        AND principal > 0
    ),

    CONSTRAINT chk_deposit_interest_rate
        CHECK (interest_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 4. LOAN DETAILS
-- Additional information for loan accounts.
-- ============================================================

CREATE TABLE loan_details (
    account_id BIGINT PRIMARY KEY,

    principal_amount DECIMAL(19,2) NOT NULL,
    annual_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tenure_months INT NOT NULL,
    start_date DATE NOT NULL,

    CONSTRAINT fk_loan_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_loan_vals
        CHECK (
        principal_amount > 0
        AND tenure_months > 0
    ),

    CONSTRAINT chk_loan_annual_rate
        CHECK (annual_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 5. TRANSACTIONS
-- Financial ledger.
--
-- The baseline application writes one SUCCESS ledger row
-- for each committed financial operation.
--
-- Other statuses are retained from the original reference
-- schema, not additional implemented workflows.
--
-- Term-deposit funding and maturity settlement use TRANSFER.
--
-- Java enforces:
--   - Ownership and account eligibility.
--   - Money precision and configurable operation limits.
--   - Locking, atomic balance updates, and ledger insertion.
--   - Correct before/after balance snapshots.
-- ============================================================

CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reference_number VARCHAR(40) NOT NULL UNIQUE,

    source_account_id BIGINT NULL,
    destination_account_id BIGINT NULL,

    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(255),

    -- Separate snapshots avoid ambiguity during transfers.
    source_balance_before DECIMAL(19,2) NULL,
    dest_balance_before DECIMAL(19,2) NULL,
    source_balance_after DECIMAL(19,2) NULL,
    dest_balance_after DECIMAL(19,2) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_source
        FOREIGN KEY (source_account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT fk_txn_dest
        FOREIGN KEY (destination_account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_txn_amount
        CHECK (amount > 0),

    CONSTRAINT chk_txn_type
        CHECK (
        transaction_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
       -- ============================================================
-- Secure Banking CLI: MySQL Schema
--
-- Nine tables and four triggers.
--
-- Financial workflows and transaction boundaries remain
-- in Java services using JDBC.
-- ============================================================


-- ============================================================
-- 1. CUSTOMERS
-- Customer details, authentication, and login lockout state.
-- ============================================================

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

    -- Store a secure password hash, never plaintext
    -- or a reversibly encrypted password.

    password_hash VARCHAR(255) NOT NULL,

    status ENUM('ACTIVE', 'LOCKED', 'CLOSED')
        NOT NULL DEFAULT 'ACTIVE',

    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_customer_status
        CHECK (
        status IN ('ACTIVE', 'LOCKED', 'CLOSED')
    ),

    CONSTRAINT chk_failed_count
        CHECK (failed_login_count >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 2. ACCOUNTS
-- Each account belongs to one customer.
--
-- Loan balance represents outstanding principal owed,
-- not money available for spending.
--
-- accounts.interest_rate stores the Savings annual percentage
-- rate. Deposit and Loan rates remain authoritative in their
-- respective detail tables.
-- ============================================================

CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,

    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,

    -- For a LOAN, balance represents outstanding principal owed.
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,

    -- Savings annual percentage rate.
    -- Deposit and Loan rates remain in their detail tables.
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,

    minimum_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    opening_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_account_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(customer_id),

    CONSTRAINT chk_account_type
        CHECK (
        account_type IN (
        'SAVINGS',
        'CURRENT',
        'DEPOSIT',
        'LOAN'
    )
),

    CONSTRAINT chk_account_status
        CHECK (
        status IN (
        'ACTIVE',
        'FROZEN',
        'MATURED',
        'CLOSED'
    )
),

    CONSTRAINT chk_account_balance
        CHECK (balance >= 0),

    CONSTRAINT chk_account_interest_rate
        CHECK (interest_rate BETWEEN 0 AND 100),

    CONSTRAINT chk_account_minimum_balance
        CHECK (minimum_balance >= 0),

    INDEX idx_accounts_customer (customer_id),
    INDEX idx_accounts_type (account_type)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 3. DEPOSIT DETAILS
-- Additional information for term-deposit accounts.
--
-- Java must verify:
--   - The associated account is a DEPOSIT account.
--   - Maturity date is consistent with the opening date
--     and the selected term.
--
-- Maturity date is stored here.
-- ============================================================

CREATE TABLE deposit_details (
    account_id BIGINT PRIMARY KEY,

    principal DECIMAL(19,2) NOT NULL,
    term_months INT NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    maturity_date DATE NOT NULL,

    CONSTRAINT fk_dep_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_dep_term
        CHECK (
        term_months > 0
        AND principal > 0
    ),

    CONSTRAINT chk_deposit_interest_rate
        CHECK (interest_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 4. LOAN DETAILS
-- Additional information for loan accounts.
-- ============================================================

CREATE TABLE loan_details (
    account_id BIGINT PRIMARY KEY,

    principal_amount DECIMAL(19,2) NOT NULL,
    annual_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tenure_months INT NOT NULL,
    start_date DATE NOT NULL,

    CONSTRAINT fk_loan_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_loan_vals
        CHECK (
        principal_amount > 0
        AND tenure_months > 0
    ),

    CONSTRAINT chk_loan_annual_rate
        CHECK (annual_rate >= 0)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 5. TRANSACTIONS
-- Financial ledger.
--
-- The baseline application writes one SUCCESS ledger row
-- for each committed financial operation.
--
-- Other statuses are retained from the original reference
-- schema, not additional implemented workflows.
--
-- Term-deposit funding and maturity settlement use TRANSFER.
--
-- Java enforces:
--   - Ownership and account eligibility.
--   - Money precision and configurable operation limits.
--   - Locking, atomic balance updates, and ledger insertion.
--   - Correct before/after balance snapshots.
-- ============================================================

CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reference_number VARCHAR(40) NOT NULL UNIQUE,

    source_account_id BIGINT NULL,
    destination_account_id BIGINT NULL,

    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(255),

    -- Separate snapshots avoid ambiguity during transfers.
    source_balance_before DECIMAL(19,2) NULL,
    dest_balance_before DECIMAL(19,2) NULL,
    source_balance_after DECIMAL(19,2) NULL,
    dest_balance_after DECIMAL(19,2) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_source
        FOREIGN KEY (source_account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT fk_txn_dest
        FOREIGN KEY (destination_account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_txn_amount
        CHECK (amount > 0),

    CONSTRAINT chk_txn_type
        CHECK (
        transaction_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER',
        'LOAN_REPAYMENT'
    )
),

    CONSTRAINT chk_txn_status
        CHECK (
        status IN (
        'PENDING',
        'SUCCESS',
        'FAILED',
        'REVERSED'
    )
),

    CONSTRAINT chk_txn_source_balance_before
        CHECK (
        source_balance_before IS NULL
        OR source_balance_before >= 0
),

    CONSTRAINT chk_txn_dest_balance_before
        CHECK (
        dest_balance_before IS NULL
        OR dest_balance_before >= 0
),

    CONSTRAINT chk_txn_source_balance_after
        CHECK (
        source_balance_after IS NULL
        OR source_balance_after >= 0
),

    CONSTRAINT chk_txn_dest_balance_after
        CHECK (
        dest_balance_after IS NULL
        OR dest_balance_after >= 0
),

    CONSTRAINT chk_txn_shape
        CHECK (
    (
        transaction_type = 'DEPOSIT'
        AND source_account_id IS NULL
        AND destination_account_id IS NOT NULL
    )
        OR
    (
        transaction_type = 'WITHDRAWAL'
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NULL
    )
        OR
    (
        transaction_type IN (
        'TRANSFER',
        'LOAN_REPAYMENT'
    )
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NOT NULL
        AND source_account_id <> destination_account_id
    )
),

        INDEX idx_txn_src_time (
        source_account_id,
        created_at,
        transaction_id
    ),

        INDEX idx_txn_dst_time (
        destination_account_id,
        created_at,
        transaction_id
    )

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 6. LOGIN AUDIT
-- Authentication events.
--
-- customer_id may be NULL for unknown attempted usernames.
-- IP address and device information are optional in a CLI.
-- Do not invent metadata the application does not collect.
--
-- Never store passwords, hashes, session secrets,
-- or raw exception details here.
-- ============================================================

CREATE TABLE login_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NULL,

    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    ip_address VARCHAR(45) NULL,
    device_info VARCHAR(255) NULL,

    -- Controlled, safe reason only.

    failure_reason VARCHAR(255) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT chk_audit_outcome
        CHECK (
        outcome IN (
        'SUCCESS',
        'FAILURE',
        'LOCKED',
        'TIMEOUT',
        'LOGOUT'
    )
),

    INDEX idx_login_time (created_at),
    INDEX idx_login_outcome (outcome)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 7. TRANSACTION AUDIT
-- Records financial attempts and their outcomes.
--
-- Java writes these records in a separate short transaction
-- AFTER the financial transaction commits or rolls back.
--
-- There is intentionally no ledger foreign key:
-- rejected/rolled-back attempts may have no committed
-- transaction row.
-- ============================================================

CREATE TABLE transaction_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,

    reference_number VARCHAR(40) NULL,
    event_type VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    -- Safe codes, such as INSUFFICIENT_FUNDS or NOT_OWNER.

    failure_code VARCHAR(40) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tauditcust
        FOREIGN KEY (customer_id)
            REFERENCES customers(customer_id),

    CONSTRAINT chk_transaction_audit_outcome
        CHECK (
        outcome IN (
        'SUCCESS',
        'REJECTED',
        'FAILED',
        'ROLLED_BACK'
    )
),
    INDEX idx_taudit_customer (
        customer_id,
        created_at
    )

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 8. ACCOUNT DAILY BALANCE
-- One closing-balance snapshot per account per calendar date.
--
-- Supports future monthly average balance and interest work.
-- A complete set of daily balances is needed, not only
-- dates on which transactions occurred.
-- ============================================================

CREATE TABLE account_daily_balance (
    snapshot_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,

    balance_date DATE NOT NULL,
    closing_balance DECIMAL(19,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_daily_balance_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_closing_balance
        CHECK (closing_balance >= 0),

    CONSTRAINT uk_account_balance_date
        UNIQUE (account_id, balance_date),

    INDEX idx_balance_date (balance_date)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 9. TRANSACTION CHANGE AUDIT
-- Records ledger-row INSERT and UPDATE changes.
--
-- Separate from financial-attempt auditing.
--
-- Populated by the triggers below. These audit rows share
-- the ledger transaction and roll back with it.
-- An audit insert failure also fails the triggering write.
-- ============================================================

CREATE TABLE transaction_change_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id BIGINT NOT NULL,

    operation VARCHAR(10) NOT NULL,

    old_amount DECIMAL(19,2) NULL,
    new_amount DECIMAL(19,2) NULL,

    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NULL,

    -- Database principal, not authenticated CLI customer.
    -- The CLI customer is recorded in transaction_audit.

    changed_by VARCHAR(288) NOT NULL,

    audit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_change_audit_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES transactions(transaction_id),

    CONSTRAINT chk_change_audit_operation
        CHECK (
        operation IN ('INSERT', 'UPDATE')
),

    INDEX idx_change_audit_transaction (transaction_id),
    INDEX idx_change_audit_time (audit_time)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- TRIGGERS
--
-- DELIMITER is a MySQL client command understood by
-- MySQL Workbench and the mysql command-line client.
-- ============================================================

DELIMITER //

-- ============================================================
-- A. VALIDATE DOB BEFORE INSERT
-- NULL/past dates allowed; today/future dates rejected.
-- CURRENT_DATE() uses the database session's time zone.
-- Apply the same date policy in Java.
-- ============================================================

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

-- ============================================================
-- B. VALIDATE DOB BEFORE UPDATE
-- ============================================================

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

-- ============================================================
-- C. RECORD LEDGER INSERT
-- Passive auditing only; does not change account balances.
-- ============================================================

CREATE TRIGGER trg_transaction_after_insert
    AFTER INSERT ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id,
        operation,
        new_amount,
        new_status,
        changed_by
    )
    VALUES (
               NEW.transaction_id,
               'INSERT',
               NEW.amount,
               NEW.status,
               CURRENT_USER()
           );
END //


-- ============================================================
-- D. RECORD LEDGER UPDATE
-- Diagnostic coverage for privileged changes.
--
-- The baseline Java application must still treat committed
-- ledger records as append-only.
-- ============================================================

CREATE TRIGGER trg_transaction_after_update
    AFTER UPDATE ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id,
        operation,
        old_amount,
        new_amount,
        old_status,
        new_status,
        changed_by
    )
    VALUES (
               NEW.transaction_id,
               'UPDATE',
               OLD.amount,
               NEW.amount,
               OLD.status,
               NEW.status,
               CURRENT_USER()
           );
END //

DELIMITER ;


-- ============================================================
-- IMPLEMENTATION NOTES
--
-- Retained change-audit.
-- rows reference the ledger and prevent deletion of audited
-- transaction rows.
--
-- Required Java/JDBC behavior remains:
--   - Customer ownership and account eligibility checks.
--   - Explicit READ COMMITTED isolation.
--   - Ascending account-ID row-lock ordering.
--   - Atomic balance updates and ledger insertion.
--   - Bounded, rollback-safe retries.
--   - Independent financial-attempt audit writes.
--   - Uncertain-commit reconciliation.
--   - Consistent minimum-balance checks on eligible outgoing
--     operations, including transfers.
--   - Deposit maturity validation against account opening date.
--   - Correct endpoint before/after balance snapshots.
--
-- Stored interest functions, interest crediting, scheduled
-- snapshots, and financial stored procedures are not included.
-- ============================================================ 'TRANSFER',
        'LOAN_REPAYMENT'
    )
),

    CONSTRAINT chk_txn_status
        CHECK (
        status IN (
        'PENDING',
        'SUCCESS',
        'FAILED',
        'REVERSED'
    )
),

    CONSTRAINT chk_txn_source_balance_before
        CHECK (
        source_balance_before IS NULL
        OR source_balance_before >= 0
),

    CONSTRAINT chk_txn_dest_balance_before
        CHECK (
        dest_balance_before IS NULL
        OR dest_balance_before >= 0
),

    CONSTRAINT chk_txn_source_balance_after
        CHECK (
        source_balance_after IS NULL
        OR source_balance_after >= 0
),

    CONSTRAINT chk_txn_dest_balance_after
        CHECK (
        dest_balance_after IS NULL
        OR dest_balance_after >= 0
),

    CONSTRAINT chk_txn_shape
        CHECK (
    (
        transaction_type = 'DEPOSIT'
        AND source_account_id IS NULL
        AND destination_account_id IS NOT NULL
    )
        OR
    (
        transaction_type = 'WITHDRAWAL'
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NULL
    )
        OR
    (
        transaction_type IN (
        'TRANSFER',
        'LOAN_REPAYMENT'
    )
        AND source_account_id IS NOT NULL
        AND destination_account_id IS NOT NULL
        AND source_account_id <> destination_account_id
    )
),

        INDEX idx_txn_src_time (
        source_account_id,
        created_at,
        transaction_id
    ),

        INDEX idx_txn_dst_time (
        destination_account_id,
        created_at,
        transaction_id
    )

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 6. LOGIN AUDIT
-- Authentication events.
--
-- customer_id may be NULL for unknown attempted usernames.
-- IP address and device information are optional in a CLI.
-- Do not invent metadata the application does not collect.
--
-- Never store passwords, hashes, session secrets,
-- or raw exception details here.
-- ============================================================

CREATE TABLE login_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NULL,

    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    ip_address VARCHAR(45) NULL,
    device_info VARCHAR(255) NULL,

    -- Controlled, safe reason only.

    failure_reason VARCHAR(255) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT chk_audit_outcome
        CHECK (
        outcome IN (
        'SUCCESS',
        'FAILURE',
        'LOCKED',
        'TIMEOUT',
        'LOGOUT'
    )
),

    INDEX idx_login_time (created_at),
    INDEX idx_login_outcome (outcome)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 7. TRANSACTION AUDIT
-- Records financial attempts and their outcomes.
--
-- Java writes these records in a separate short transaction
-- AFTER the financial transaction commits or rolls back.
--
-- There is intentionally no ledger foreign key:
-- rejected/rolled-back attempts may have no committed
-- transaction row.
-- ============================================================

CREATE TABLE transaction_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,

    reference_number VARCHAR(40) NULL,
    event_type VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    -- Safe codes, such as INSUFFICIENT_FUNDS or NOT_OWNER.

    failure_code VARCHAR(40) NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tauditcust
        FOREIGN KEY (customer_id)
            REFERENCES customers(customer_id),

    CONSTRAINT chk_transaction_audit_outcome
        CHECK (
        outcome IN (
        'SUCCESS',
        'REJECTED',
        'FAILED',
        'ROLLED_BACK'
    )
),
    INDEX idx_taudit_customer (
        customer_id,
        created_at
    )

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 8. ACCOUNT DAILY BALANCE
-- One closing-balance snapshot per account per calendar date.
--
-- Supports future monthly average balance and interest work.
-- A complete set of daily balances is needed, not only
-- dates on which transactions occurred.
-- ============================================================

CREATE TABLE account_daily_balance (
    snapshot_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL,

    balance_date DATE NOT NULL,
    closing_balance DECIMAL(19,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_daily_balance_account
        FOREIGN KEY (account_id)
            REFERENCES accounts(account_id),

    CONSTRAINT chk_closing_balance
        CHECK (closing_balance >= 0),

    CONSTRAINT uk_account_balance_date
        UNIQUE (account_id, balance_date),

    INDEX idx_balance_date (balance_date)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 9. TRANSACTION CHANGE AUDIT
-- Records ledger-row INSERT and UPDATE changes.
--
-- Separate from financial-attempt auditing.
--
-- Populated by the triggers below. These audit rows share
-- the ledger transaction and roll back with it.
-- An audit insert failure also fails the triggering write.
-- ============================================================

CREATE TABLE transaction_change_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id BIGINT NOT NULL,

    operation VARCHAR(10) NOT NULL,

    old_amount DECIMAL(19,2) NULL,
    new_amount DECIMAL(19,2) NULL,

    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NULL,

    -- Database principal, not authenticated CLI customer.
    -- The CLI customer is recorded in transaction_audit.

    changed_by VARCHAR(288) NOT NULL,

    audit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_change_audit_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES transactions(transaction_id),

    CONSTRAINT chk_change_audit_operation
        CHECK (
        operation IN ('INSERT', 'UPDATE')
),

    INDEX idx_change_audit_transaction (transaction_id),
    INDEX idx_change_audit_time (audit_time)

) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- TRIGGERS
--
-- DELIMITER is a MySQL client command understood by
-- MySQL Workbench and the mysql command-line client.
-- ============================================================

DELIMITER //

-- ============================================================
-- A. VALIDATE DOB BEFORE INSERT
-- NULL/past dates allowed; today/future dates rejected.
-- CURRENT_DATE() uses the database session's time zone.
-- Apply the same date policy in Java.
-- ============================================================

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

-- ============================================================
-- B. VALIDATE DOB BEFORE UPDATE
-- ============================================================

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

-- ============================================================
-- C. RECORD LEDGER INSERT
-- Passive auditing only; does not change account balances.
-- ============================================================

CREATE TRIGGER trg_transaction_after_insert
    AFTER INSERT ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id,
        operation,
        new_amount,
        new_status,
        changed_by
    )
    VALUES (
               NEW.transaction_id,
               'INSERT',
               NEW.amount,
               NEW.status,
               CURRENT_USER()
           );
END //


-- ============================================================
-- D. RECORD LEDGER UPDATE
-- Diagnostic coverage for privileged changes.
--
-- The baseline Java application must still treat committed
-- ledger records as append-only.
-- ============================================================

CREATE TRIGGER trg_transaction_after_update
    AFTER UPDATE ON transactions
    FOR EACH ROW
BEGIN
    INSERT INTO transaction_change_audit (
        transaction_id,
        operation,
        old_amount,
        new_amount,
        old_status,
        new_status,
        changed_by
    )
    VALUES (
               NEW.transaction_id,
               'UPDATE',
               OLD.amount,
               NEW.amount,
               OLD.status,
               NEW.status,
               CURRENT_USER()
           );
END //

DELIMITER ;


-- ============================================================
-- IMPLEMENTATION NOTES
--
-- Retained change-audit.
-- rows reference the ledger and prevent deletion of audited
-- transaction rows.
--
-- Required Java/JDBC behavior remains:
--   - Customer ownership and account eligibility checks.
--   - Explicit READ COMMITTED isolation.
--   - Ascending account-ID row-lock ordering.
--   - Atomic balance updates and ledger insertion.
--   - Bounded, rollback-safe retries.
--   - Independent financial-attempt audit writes.
--   - Uncertain-commit reconciliation.
--   - Consistent minimum-balance checks on eligible outgoing
--     operations, including transfers.
--   - Deposit maturity validation against account opening date.
--   - Correct endpoint before/after balance snapshots.
--
-- Stored interest functions, interest crediting, scheduled
-- snapshots, and financial stored procedures are not included.
