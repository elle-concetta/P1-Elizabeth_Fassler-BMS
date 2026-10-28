# P1: Secure Banking CLI

A standalone, menu-driven Java application for learning secure authentication, account authorization, JDBC, SQL, and atomic financial operations.

## Goals and Scope

- Register customers and authenticate using salted adaptive password hashes.
- Manage customer profiles, password changes, and multiple owned accounts.
- Support Savings, Current, term Deposit, and Loan account records.
- Deposit, withdraw, transfer funds atomically, settle matured deposits, and repay loans.
- Display owned balances and filtered, paginated transaction history.
- Enforce inactivity expiry, explicit logout, ownership, eligibility, and input validation.
- Record authentication and financial outcomes without exposing secrets.
- Demonstrate rollback, database constraints, and safe concurrent operations through automated tests.

Out of scope: Spring Boot, React, other web frameworks, a GUI, REST APIs, real payment processing, loan disbursement, interest calculation, MFA, distributed services, and production deployment.

## Technology baseline

| Area | Selected implementation |
|---|---|
| Language | Java 17 |
| Build | Maven 3.9+ |
| Persistence | JDBC with MySQL 8.0.16+ and InnoDB |
| Optional database variant | PostgreSQL 14+; requires its own schema and integration verification |
| Money | Immutable `Money` wrapping `BigDecimal`; SQL `DECIMAL(19,2)` |
| Password hashing | bcrypt through `PasswordHasher`; default cost 12 |
| Logging | SLF4J and Logback |
| Tests | JUnit 5; Mockito or fake DAOs; dedicated test database |
| Packaging | Runnable JAR with dependencies and `BankingApplication` as entry point |


Maven dependencies: `com.mysql:mysql-connector-j`, `at.favre.lib:bcrypt`, `org.slf4j:slf4j-api`, `ch.qos.logback:logback-classic`, `org.junit.jupiter:junit-jupiter`, and optionally `org.mockito:mockito-core`. Pin supported versions during implementation; configure compiler, Surefire, Failsafe, and Shade plugins explicitly.

## Account Rules

| Type | Meaning of balance | Allowed behavior |
|---|---|---|
| `SAVINGS` | Spendable cash | Deposit, withdraw, send/receive ordinary transfers; minimum balance defaults to zero |
| `CURRENT` | Spendable cash | Same baseline operations as Savings; overdraft is zero |
| `DEPOSIT` | Locked principal | Fund once from an owned Savings/Current account; settle to an owned Savings/Current account only after maturity |
| `LOAN` | Outstanding principal owed | Repay from an owned Savings/Current account; no ordinary deposit, withdrawal, or transfer |

- Ordinary transfers require an owned source and an existing eligible destination. The destination may belong to another customer; this does not grant access to that customer's profile, balance, or history.
- Deposit opening and settlement use dedicated workflows, although their ledger rows have type `TRANSFER`. They must not make Deposit accounts eligible for ordinary transfers.
- Deposit maturity is evaluated on login/listing using an injectable clock; early settlement is rejected. Settlement moves principal only and closes the account at zero balance.
- Loan creation records a positive principal and terms without crediting spendable cash. Repayment cannot exceed outstanding principal; exact repayment closes the loan.
- Persisted account states are `ACTIVE`, `FROZEN`, `MATURED`, and `CLOSED`. `CREATED` is in-memory only. `MATURED` applies only to Deposit accounts.
- Frozen/closed accounts cannot transact. Matured deposits permit only settlement. No customer-facing administrative menu is required.

## Architecture

```text
Terminal menus and input handling (app)
                  |
Application services: session, authorization, orchestration
                  |
Domain model and policies: money, eligibility, account lifecycle
                  |
DAO interfaces -> JDBC implementations -> relational database

Cross-cutting: security, validation, typed exceptions, configuration, logging
Audit: independent short transactions after the primary outcome
```

Services own transaction boundaries. DAOs receive the same JDBC `Connection` for a unit of work and never commit independently. Domain code must not depend on JDBC or terminal input; menus must not contain SQL or financial rules.

DDD is deliberately lightweight: Identity & Access, Account Management, Ledger & Transactions, and Audit & Observability are logical modules in one process, not microservices. Aggregates reference one another by ID. A transfer deliberately updates two account aggregates in one database transaction to satisfy atomicity.

### Target repository structure

```text
banking-cli/
├── pom.xml
├── README.md
├── IMPLEMENTATION_PLAN.md
├── docs/decisions.md
├── sql/
│   ├── mysql/schema.sql
│   ├── postgres/schema.sql          # optional, after baseline verification
│   └── sample-data.sql             # optional synthetic data; no reusable passwords
├── src/main/resources/
│   ├── app.properties
│   └── logback.xml
├── src/main/java/com/example/banking/
│   ├── app/                       # BankingApplication, menus, ConsoleInput
│   ├── model/                     # Customer, Account hierarchy, Money, ledger, enums
│   ├── model/policy/              # eligibility, transfer, password, lockout policies
│   ├── service/                   # Auth, Customer, Account, Transaction, Session
│   ├── dao/
│   │   └── jdbc/                  # parameterized SQL and ResultSet mapping
│   ├── security/                  # PasswordHasher, bcrypt, SessionContext
│   ├── validation/
│   ├── exception/
│   └── util/                      # AppConfig, connections, transactions, references
└── src/test/java/com/example/banking/
    ├── unit/
    ├── service/
    └── integration/
```

## Database

| Table | Responsibility |
|---|---|
| `customers` | Profile, unique username/email, encoded password hash, lockout state |
| `accounts` | Owner, unique account number, type, nonnegative balance, status |
| `deposit_details` | Deposit principal, term, maturity date, zero-interest baseline |
| `loan_details` | Original principal, tenure, start date, zero-interest baseline |
| `transactions` | One immutable ledger row per completed business transaction |
| `login_audit` | Authentication, lockout, timeout, and logout outcomes |
| `transaction_audit` | Financial successes, rejections, failures, and rollbacks |

Enforce primary/foreign keys, unique identifiers, positive transaction amounts, valid enums, nonnegative balances, and valid source/destination shapes. Index ownership and account-history queries.

The reference schema permits several transaction statuses, but baseline ledger writes are committed `SUCCESS` rows only. Failed attempts belong in audit, not as successful money movement. Loan repayment reduces debt: a destination ledger field must not be displayed as spendable cash credited to the loan.

## Security and transaction guarantees

- Derive identity from the active session, never a customer ID entered at a menu.
- Check session and account ownership at every protected service entry point.
- Use `PreparedStatement` for values; whitelist any dynamic identifiers.
- Hash passwords with a library; never store plaintext, reversible encryption, or plain SHA-256.
- Use generic login failures and dummy-hash verification for unknown usernames. This reduces timing differences; it is not a guarantee of perfectly constant response time.
- Default to five failed attempts and a 15-minute temporary lockout; update counters atomically.
- Use `char[]` for password handling where possible and clear buffers. Do not silently truncate bcrypt input; define and test the library's UTF-8 byte-length limit.
- Use `System.console().readPassword()` for hidden input in a real terminal. If a non-console fallback is supported, warn explicitly when input cannot be hidden.
- Never log passwords, hashes, credentials, or full account numbers.

For every money operation:

1. Validate the amount and authorize the session.
2. Open one connection, set `READ_COMMITTED`, and disable auto-commit.
3. Lock all existing affected accounts in ascending `account_id` order using `SELECT ... FOR UPDATE`.
4. Recheck ownership, status, eligibility, funds, and destination capacity under those locks.
5. Persist balance changes and one ledger row on the same connection.
6. Commit once, or roll back all changes; restore connection state and close resources.
7. Write the resulting audit event in its own short transaction.

Retry only classified transient deadlock/lock-wait errors after confirmed rollback, with bounded backoff and **three total attempts**. Generate one operation reference before the retry loop and preserve it.

Unique references alone do not make arbitrary retries safe. On uncertain commit outcomes, reconcile by reference using a fresh connection before deciding what happened; do not blindly repeat the debit. An audit write failing after commit must not cause the financial operation to run again or be reported as rolled back. Independent audit writes have a crash/unavailability gap; document it rather than claiming guaranteed audit delivery.

## Setup and run — target workflow

These commands describe the application to be built; they have not been executed against an implementation.

### 1. Install prerequisites

Install Java 17, Maven 3.9+, and MySQL 8.0.16+ with InnoDB.

```bash
java -version
mvn -version
```

### 2. Create isolated databases and apply the schema

Create `banking` and `banking_test` using a database administrator. Apply `sql/mysql/schema.sql` to each empty database. The following imports assume those databases already exist and schema files have been implemented:

```bash
mysql -u <schema_admin> -p banking < sql/mysql/schema.sql
mysql -u <schema_admin> -p banking_test < sql/mysql/schema.sql
```

Use separate runtime and test principals. Keep DDL privileges on the schema setup account, not the runtime account. Grant the runtime only the required reads/writes; ledger and audit tables should not allow runtime update/delete. Keep test cleanup privileges isolated from runtime.

### 3. Configure credentials privately

Supply these variables through your local environment or a secrets manager; do not commit their values:

| Variable | Purpose |
|---|---|
| `DB_URL` | Application JDBC connection URL |
| `DB_USERNAME` | Least-privilege runtime user |
| `DB_PASSWORD` | Runtime credential |
| `TEST_DB_URL` | Dedicated integration-test JDBC URL; proposed project convention |
| `TEST_DB_USERNAME` | Isolated test user; proposed project convention |
| `TEST_DB_PASSWORD` | Test credential; proposed project convention |

Tests must fail closed when test configuration is missing or points at the application database. Never fall back to `DB_*` for integration tests.

### 4. Configure non-secret defaults

Target `src/main/resources/app.properties`:

```properties
session.idle.timeout.seconds=300
db.query.timeout.seconds=10
tx.max.amount=1000000.00
auth.max.failed.logins=5
auth.lock.minutes=15
password.hash.algorithm=bcrypt
password.hash.cost=12
history.max.page.size=50
```

The explicit `password.hash.cost` spelling is a project convention. Configure a database lock-wait timeout separately; JDBC query timeout does not reliably replace it. Fail startup on missing credentials or invalid configuration without printing secrets.

### 5. Test, package, and run

The recommended Maven convention separates database-free tests from database integration tests:

```bash
mvn test                         # unit/service tests via Surefire
mvn verify -Pintegration         # also runs *IT tests via Failsafe against banking_test
mvn clean package                # Shade creates target/banking-cli.jar
java -jar target/banking-cli.jar
```

Implement the `integration` profile and final JAR name in `pom.xml` before using these commands. Packaging alone does not prove integration tests passed.

## Usage — proposed menus

Welcome: Register, Login, Exit.

Authenticated: Profile, Change password, Open account, List accounts/balances, Deposit, Withdraw, Transfer, Open/settle term deposit, Repay loan, History, Logout.

Use decimal strings, not floating-point input. Operation amounts must be positive, have no more than two fractional digits, and stay within the configured maximum. Reject excess fractional digits rather than rounding. Savings/Current start at zero; Deposit and Loan follow their dedicated creation rules.

History requires ownership and supports date/type filters, page numbers starting at zero, and page sizes 1–50. Order by `created_at DESC, transaction_id DESC`. This provides deterministic ordering but offset pages can shift during concurrent inserts; document that limitation or add snapshot/keyset pagination later.

Inactivity is detected when blocked input returns. Check expiry before refreshing activity and again before a protected action after any prompts. Logout invalidates the session. The baseline does not need a watchdog thread.

## Verification and acceptance

Automate the source document's T01–T18 scenarios:

- Registration, duplicates, hashing, valid/invalid/unknown-user login, and temporary lockout.
- Cross-customer access denial, positive deposit, insufficient withdrawal, and eligible transfer.
- Forced failure after debit, ineligible/self-transfer, and session expiry.
- Invalid amounts, concurrent withdrawals/opposing transfers, and history boundaries.
- Early/matured Deposit settlement, partial/exact/excess Loan repayment, and SQL-injection strings.

Also test password changes, logout, audit persistence after rollback, account state restrictions, lockout expiry, reference reuse, uncertain commits, and post-commit audit failures. Use real MySQL integration tests for locking and rollback; mocks alone cannot establish database behavior.

Final acceptance requires a clean setup, all four account types, no secret leakage, no unauthorized access, no overspending, no partial transfers, parameterized SQL, and passing relevant automated tests. Reports should distinguish tests actually run from planned/manual checks.

## Troubleshooting

| Symptom | Check |
|---|---|
| Connection fails | Database availability, JDBC driver, private environment configuration, and account privileges |
| Constraints do not reject invalid data | MySQL version, InnoDB, actual schema, and enforced CHECK constraints |
| Integration tests target the wrong database | Stop immediately; correct isolated `TEST_DB_*` configuration and safety guard |
| JAR does not launch | Manifest main class, included dependencies, Shade configuration, and final filename |
| Password entry is visible | Run in a real terminal or explicitly acknowledge the non-console fallback |
| Timeout notice appears only after typing | Expected blocking-CLI behavior; the returned input must not execute an expired action |
| Repeated lock failures | Verify lock order, transaction duration, rollback, SQL error classification, and bounded retries |
| Audit unavailable after successful operation | Preserve the committed receipt/reference; warn safely and reconcile without repeating money movement |

## Development and AI-assistance log

Keep meaningful commits and record any AI-assisted work. Every security and transaction change must be understood and verified by the developer.

| Contribution | Assistance | Verification |
|---|---|---|
| README and implementation plan | AI-assisted drafting from the supplied v2.0 PDF | Compared with the design; application code/build/runtime not supplied or verified |
| Future implementation changes | Record scope and whether assistance was used | Record actual tests, review, corrections, and remaining limitations |

## Future work

After P1 acceptance: optional PostgreSQL verification, interest/penalty rules, overdraft, loan schedules/disbursement, statement exports, migrations, stronger audit delivery, and a later service/API project. Do not expand these before the baseline integrity and security gates pass.
