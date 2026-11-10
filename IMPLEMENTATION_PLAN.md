# Step-by-step Implementation Plan

## Project and scope

**Project:** P1-Elizabeth_Fassler-BMS  
**Source:** P1 — Secure Banking CLI, HLD / LLD / DDD, version 2.0
**Stack:** Java 17, Maven 3.9+, MySQL 8.0.16+ with InnoDB, JDBC, JUnit 5, Mockito, SLF4J, Logback, bcrypt.

This is a build guide, not a claim that these features are implemented. The current application is a scaffold. Implement one numbered step at a time and pass its completion checks before continuing.

The application is a menu-driven terminal program. MySQL is the selected database.

**Start with step 1: verify Java and Maven and complete the Maven test setup. Then complete steps 2–4. The first banking class to implement is `Money.java` in step 5. Do not start by implementing all the menus.**

## How the required technologies fit together

| Technology        | Purpose                                                                             | Introduce in         |
|-------------------|-------------------------------------------------------------------------------------|----------------------|
| Maven             | Resolve dependencies, compile, run tests, package an executable JAR                 | Step 1               |
| MySQL Connector/J | JDBC driver for real MySQL connections and SQL operations                           | Steps 1, 3–4         |
| JUnit Jupiter     | Assertions and automated unit, service, JDBC and transaction tests                  | Step 1 onward        |
| Mockito           | Mock DAOs and JDBC `Connection`, `PreparedStatement`, and `ResultSet` collaborators | Steps 1, 8–10 onward |
| SLF4J             | Logging API used by Java classes                                                    | Step 4 onward        |
| Logback           | Logging implementation and output configuration                                     | Step 4 onward        |
| bcrypt            | Salted adaptive password hashing and verification                                   | Step 7 onward        |

Passwords must be **hashed, not encrypted**. Never store plaintext, reversible encryption, or a plain SHA-256 digest. Use the hashing library rather than writing cryptographic code.

Mockito tests do not prove that SQL is valid or that MySQL locking and rollback work. Use mocked JDBC objects for interaction/failure-path tests, and a separate real `banking_test` database for integration and concurrency tests.

## Working rules for every step

- Keep project files at the repository root; do not introduce an extra enclosing project folder.
- `src/main/java/...` holds `.java` source files. `app.properties` and `logback.xml` remain configuration files in `src/main/resources`.
- Menus parse and display; services coordinate use cases and transactions; domain objects/policies enforce rules; JDBC DAOs contain SQL and map rows.
- Services never read terminal input. Domain objects never depend on JDBC. DAOs never independently commit a caller's transaction.
- Write tests alongside each feature, not only during final testing.
- Make small, meaningful Git commits after verified increments.
- Use synthetic customers only. Never commit credentials, reusable passwords, hashes used as credentials, or database dumps containing sensitive data.

---

## Phase 1 — Foundation (document milestone P1-M1)

### Step 1 — Verify Java, Maven, dependencies, and the test runner

**Document:** sections 13, 25–27.

**Work on:** `pom.xml`, `app/BankingApplication.java`, the existing test directories.

1. Run `java -version` and `mvn -version`; select and document JDK 17 and Maven 3.9+.
2. Open the root `pom.xml` as a Maven project and use Java 17 for compilation.
3. Confirm dependencies for MySQL Connector/J, bcrypt, SLF4J API, Logback Classic and JUnit Jupiter.
4. Add `org.mockito:mockito-core` and `org.mockito:mockito-junit-jupiter` with test scope and compatible, pinned versions. They are not present in the current scaffold's POM.
5. Use `@ExtendWith(MockitoExtension.class)` for tests that use Mockito annotations.
6. Keep Surefire for `*Test.java`; use the existing Failsafe integration profile for `*IT.java`.
7. Add one small JUnit test that actually executes. Confirm the build reports a nonzero test count.
8. Keep the entry point minimal until startup composition and menus are ready.

**Complete when:** `mvn test` executes a real passing test and `mvn clean package` creates `target/banking-cli.jar`.

**Next:** record the project's rules and unresolved design choices.

### Step 2 — Turn the document into a checklist and decision record

**Document:** sections 4–6, 19, 21, 28–38.

**Work on:** `docs/decisions.md`, `README.md`.

1. Track F01–F14, INV-01–INV-12 and T01–T18 using the tables at the end of this plan.
2. Review the class, layer, sequence, state and ER diagrams against the planned implementation.
3. Record the four account meanings:
   - Savings/Current: spendable funds, minimum balance zero, no overdraft.
   - Deposit: locked principal, funded from an owned Savings/Current account.
   - Loan: outstanding principal owed, never spendable cash.
4. Record persisted account statuses: ACTIVE, FROZEN, MATURED, CLOSED. CREATED is in-memory only.
5. Decide and document items the source does not fully specify: account-number format/checksum, term bounds, minimum term-deposit principal, password maximum compatible with bcrypt, field normalization, and permitted profile edits.
6. Resolve document ambiguities explicitly:
   - Temporary lockout must expire; do not leave a customer permanently LOCKED after `locked_until`.
   - Use consistent pagination validation despite the document mentioning both clamping and rejection. Recommended: reject page sizes outside 1–50.
   - Normal transfers require an owned source; the destination may be another customer's eligible account, but its private profile/balance must not be disclosed.
   - Dedicated Deposit funding/settlement and Loan repayment are exceptions to normal endpoint eligibility, not generic transfers that bypass policy.
7. Keep logical DDD contexts inside this one process. Do not create microservices.

**Complete when:** the checklist and decisions explain the baseline without inventing new banking features.

**Next:** create the MySQL schema.

### Step 3 — Implement the seven-table MySQL schema

**Document:** sections 8, 11, 17, 35.

**Work on:** `sql/mysql/schema.sql`; optional `sql/sample-data.sql`.

1. Create local databases `banking` and `banking_test`.
2. Use MySQL 8.0.16+ so CHECK constraints are enforced, and InnoDB for transactional tables.
3. Translate section 17.1 into these seven tables, in dependency order:
   1. `customers`
   2. `accounts`
   3. `deposit_details`
   4. `loan_details`
   5. `transactions`
   6. `login_audit`
   7. `transaction_audit`
4. Preserve required columns, lengths, timestamps, PKs, FKs, UNIQUE constraints and CHECK constraints from the reference DDL.
5. Use `DECIMAL(19,2)` for balances and operation amounts.
6. Enforce transaction shape: deposit has destination only; withdrawal source only; transfer/repayment have distinct source and destination.
7. Add customer/account and source/destination history indexes from the document.
8. Use a setup user for DDL and a least-privilege application user for runtime DML. Do not run the application as root.
9. Configure runtime permissions so audit writers append records rather than updating/deleting them.
10. Create mock data for database from Mockaroo

**Test:** duplicate usernames/emails/numbers/references, invalid FK, negative balance, zero amount, invalid status/type and malformed ledger rows must fail.

**Complete when:** a clean database can be created from the script and its constraints reject invalid data.

**Next:** configuration, safe logging and connections.

### Step 4 — Implement configuration, logging and connection creation

**Document:** sections 10, 12, 23–24.

**Work on:** `util/AppConfig.java`, `ConnectionFactory.java`, `LoggerFactory.java`, `src/main/resources/app.properties`, `logback.xml`.

1. Load and validate non-secret configuration:

   | Key                            | Default    |
      |--------------------------------|------------|
   | `session.idle.timeout.seconds` | 300        |
   | `db.query.timeout.seconds`     | 10         |
   | `tx.max.amount`                | 1000000.00 |
   | `auth.max.failed.logins`       | 5          |
   | `auth.lock.minutes`            | 15         |
   | `password.hash.algorithm`      | bcrypt     |
   | `password.hash.cost`           | 12         |
   | `history.max.page.size`        | 50         |

2. Read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` from private environment configuration. Fail clearly when required values are absent; never print their values.
3. Give integration tests separate `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` configuration.
4. Before test setup/cleanup, verify the selected database is exactly the dedicated test database and differs from the application database. Refuse unsafe cleanup.
5. Make `ConnectionFactory` injectable so tests can supply a mock connection without replacing a static `DriverManager` call.
6. Verify the driver can connect and return a trivial query result. Keep SQL in the JDBC layer or integration checks.
7. Configure SLF4J through Logback. Use levels and parameterized messages with correlation references.
8. Never log passwords, hashes, database credentials, full connection URLs with credentials, or full account numbers. Mask account numbers to their last four characters.
9. Keep application diagnostics separate from the normal menu output.

**Test:** missing/invalid settings, connection failure and sensitive-data redaction.

**Complete when:** the application connects using external credentials and produces safe logs without exposing secrets.

**Next:** implement money before account operations.

---

## Phase 2 — Domain, security and persistence foundations

### Step 5 — Implement Money and foundational value objects

**Document:** sections 5, 14, 29, 31, 35.

**Work on first:** `model/Money.java`, then `AccountNumber.java`, the existing enums and `util/ReferenceGenerator.java`.

1. Make Money immutable and backed by `BigDecimal`, never `double` or `float`.
2. Parse decimal strings. Reject nulls and inputs with more than two decimal places; normalize accepted amounts to scale 2 with `RoundingMode.UNNECESSARY`.
3. Implement addition, subtraction, comparison, equality, hashing and positive-value checks.
4. Allow zero for balances; separately require operation amounts to be positive and no greater than the configured limit.
5. Match enum values to SQL CHECK constraints.
6. Implement controlled account-number and transaction-reference generation. Keep values within database column lengths and rely on UNIQUE constraints as the final collision guard.
7. Add section 31's value types as needed: `TransactionReference`, `Username`, `Email`, `PasswordHash`, `DepositTerms`, `LoanTerms`, and `AuditOutcome`.
8. Keep hashes out of `toString()` and other automatic object rendering.
9. Provide the section 32 account-number generator contract, either as a focused `AccountNumberGenerator` or a documented responsibility of the existing generator.

**JUnit tests:** `10`, `10.1`, `10.12`, addition/subtraction, equality, null, malformed input, `10.123` and `10.120`, zero/negative operation amounts, operation-limit boundary, generator format.

**Complete when:** Money and value-object tests pass without a database.

**Next:** account behavior and validation.

### Step 6 — Implement entities, policies, validators and typed exceptions

**Document:** sections 5.1, 14, 19, 21–22, 31–32, 35.

**Work on:** `model/`, `model/policy/`, `validation/`, `exception/`.

1. Implement Customer and the abstract Account with identity, owner ID, type, balance, status and timestamps.
2. Implement SavingsAccount, CurrentAccount, DepositAccount and LoanAccount according to their different behaviors.
3. Do not expose a public balance setter. Domain methods enforce rules; services invoke mutations only inside the locked transaction flow.
4. Implement `AccountEligibilityPolicy`, `TransferPolicy`, `PasswordPolicy`, `LockoutPolicy`.
5. Enforce no overdraft, no customer transactions on FROZEN/CLOSED accounts, and settlement-only behavior for MATURED deposits.
6. Model Loan balance as debt. Normal withdrawal/credit/debit APIs must not treat it as spendable funds; provide explicit repayment behavior.
7. Implement immutable BankTransaction factories with required source/destination IDs and balance-after snapshots.
8. Implement append-only audit models.
9. Implement the validators. Username is 3–30 characters using letters, digits, `.`, `_`, `-`; validate names, email, phone, account parameters, amounts and pagination.
10. Implement all exceptions in section 22. Preserve technical causes for restricted logging but expose only safe messages.

**JUnit tests:** account operation matrix, status transitions, insufficient funds, ledger shape, terms, validation boundaries and typed failures.

**Complete when:** domain invariants can be demonstrated without JDBC or menus.

**Next:** password hashing.

### Step 7 — Implement secure password hashing

**Document:** sections 10, 14, 18.1–18.2, 21.

**Work on:** `security/PasswordHasher.java`, `BcryptPasswordHasher.java`, `model/policy/PasswordPolicy.java`.

1. Keep `hash(char[])` and `verify(char[], String)` behind PasswordHasher.
2. Use bcrypt cost 12 by default, with a fresh library-generated salt for each hash.
3. Enforce minimum 10 characters, mixed character classes, and password not equal to username.
4. Define a bcrypt-compatible maximum in UTF-8 bytes; reject oversized input rather than silently truncating it.
5. Use a fixed valid dummy hash to verify unknown usernames without creating a new hash per attempt.
6. Clear raw password arrays in `finally` blocks. Avoid creating unnecessary immutable String copies.

**JUnit tests with the real library:** correct password, wrong password, two different hashes for the same password, hash verification, policy violations and byte-length boundaries.

**Complete when:** only an encoded hash is needed to authenticate and no raw password is persisted or logged.

**Next:** DAO contracts.

### Step 8 — Define DAO contracts and implement customer/account JDBC mappings

**Document:** sections 14–17, 34.

**Work on:** `dao/`, `dao/jdbc/`.

1. Define the document's operations for CustomerDAO, AccountDAO, TransactionDAO, LoginAuditDAO and TransactionAuditDAO.
2. Accept a caller-owned Connection for operations participating in one unit of work.
3. Implement customer insert/lookups/profile/password/failed-login updates.
4. Implement account insert, owned lookup, number lookup, customer listing, status/balance updates and type-detail persistence.
5. Map each account row to the correct subclass, including Deposit/Loan terms.
6. Use PreparedStatement for every user-supplied value. Whitelist any dynamic SQL identifiers.
7. Use `getBigDecimal`/`setBigDecimal` for money and a consistent documented time strategy.
8. Close statements/result sets with try-with-resources; do not close a caller-owned connection inside a DAO.
9. Check affected-row counts and handle missing records explicitly.
10. Map uniqueness races to safe validation errors and other SQL failures to persistence failures.

**Mockito tests:** mock Connection, PreparedStatement and ResultSet; verify parameter binding, row mapping, generated keys, resource closure and safe SQL exception translation.

**Real MySQL tests:** insert/read/update, generated IDs, owned queries, uniqueness/FK/CHECK violations and all subclass mappings.

**Complete when:** DAOs pass both mocked interaction tests and real database tests; no DAO commits caller work.

**Next:** the transaction boundary.

### Step 9 — Implement transaction handling, row locking and bounded retry

**Document:** sections 6.1, 12, 18.3–18.4, 31.2.

**Work on:** `util/JdbcTransaction.java`, `AccountDAO` and its JDBC implementation.

1. Open one connection for the entire unit of work.
2. Explicitly set isolation to `READ_COMMITTED`; disable auto-commit.
3. Commit once after all changes and the ledger insert succeed.
4. Roll back on failure; restore changed connection state and close owned resources without hiding the original exception.
5. Lock all existing affected account rows with `SELECT ... FOR UPDATE` in ascending account-ID order.
6. Re-read balances, ownership, type and status under those locks.
7. Apply statement query timeout and an appropriate MySQL lock-wait timeout; do not confuse either with session idle timeout.
8. Retry only recognized transient deadlock/lock-timeout cases after the whole unit has rolled back. Use at most three total attempts with short randomized backoff.
9. Recognize MySQL deadlock error 1213 / SQLState 40001 and lock-wait timeout 1205 appropriately. Do not retry every SQLException.
10. Generate one business reference before entering the retry loop and preserve it across attempts.
11. A unique reference alone is not a complete idempotency algorithm: check an existing reference and confirm it matches the intended operation.
12. If commit outcome is uncertain after connection loss, do not blindly repeat the debit. Reconcile by reference using a new connection; report an unresolved outcome safely if reconciliation is impossible.

**Mockito tests:** commit once, rollback on callback failure, state restoration, close, bounded retry classification, commit failure and no retry of a confirmed success.

**Real MySQL tests:** rollback restores changes; separate connections actually block/serialize correctly.

**Complete when:** transaction cleanup, locking and retry behavior are proven before financial features use them.

**Next:** independent audit writers and events.

### Step 10 — Implement audit persistence and in-process domain events

**Document:** sections 16, 24, 30, 33–35.

**Work on:** audit DAOs/JDBC implementations, audit models, a small `EventPublisher` interface and synchronous implementation in `service` or `util`.

1. Implement append-only login_audit and transaction_audit inserts.
2. Give each audit write its own short transaction.
3. Add immutable events from section 33:
   - CustomerRegistered, LoginSucceeded, LoginFailed, AccountLockedOut.
   - SessionExpired, LoggedOut, AccountOpened.
   - MoneyDeposited, MoneyWithdrawn, TransferCompleted.
   - TransferRejected, TransferFailed, LoanRepaid, LoanClosed.
4. Publish successful business events only after primary commit.
5. Publish rejected/failed outcomes after the financial transaction has ended or rolled back.
6. Audit subscribers persist safe fields: customer ID, reference, event type, outcome, failure code, timestamp.
7. Never let an audit insert failure trigger a second execution of an already committed money operation. Log it distinctly and document the recovery limitation.
8. Avoid double audit inserts: use either the event subscriber or a direct audit call for a given outcome, not both.
9. Keep optional registration/account-open auditing clearly distinguished from required login and financial outcome auditing.

**Test:** event timing, audit persistence after rollback, append-only behavior, secret-free payloads and post-commit audit failure.

**Complete when:** the ledger and the audit trail have separate, tested responsibilities.

**Next:** deliver the first complete user feature: registration.

---

## Phase 3 — Identity and customer features (P1-M2)

### Step 11 — Implement registration

**Document:** F01, sections 18.1, 21; tests T01–T02.

**Work on:** `service/AuthService.java`, CustomerValidator, CustomerDAO.

1. Validate fields and password policy.
2. Check username/email uniqueness for a friendly response.
3. Hash the password and save an ACTIVE customer with safe initial lockout state.
4. Still handle a database UNIQUE violation if another registration wins the race.
5. Clear raw password arrays and publish the committed registration event as applicable.

**JUnit + Mockito:** valid registration, bad fields, weak password, duplicate precheck, duplicate insert race; verify only the hash is passed to persistence.

**Real MySQL:** inspect a synthetic customer's stored value and verify it is an encoded hash, not plaintext.

**Complete when:** T01 and T02 pass with no partial customer on failure.

**Next:** session lifecycle, which login will use.

### Step 12 — Implement sessions before connecting protected features

**Document:** F11, section 20; T12.

**Work on:** SessionContext, SessionManager, `service/SessionService.java`.

1. Track customerId, createdAt and lastActivity in memory.
2. Inject `java.time.Clock`; do not hard-code current time throughout the application.
3. Expire at the configured idle boundary, default 300 seconds.
4. Check expiry before refreshing activity. An expired session must not become valid merely because input arrived.
5. Invalidate state on timeout/logout and publish the appropriate audit event once.
6. Require an active session before every protected action; derive customer identity from that session, never typed input.

**JUnit:** just-before/exactly-at/after expiry, activity refresh, logout and reuse denial using a controllable Clock without sleeping.

**Complete when:** expired/logged-out sessions cannot call protected services.

**Next:** login and lockout.

### Step 13 — Implement login, temporary lockout and authentication audit

**Document:** F02, sections 10, 18.2; T03–T05.

**Work on:** AuthService, CustomerDAO, LockoutPolicy, SessionManager, LoginAuditDAO/event subscriber.

1. Reject blank credentials safely.
2. Fetch by username with a prepared query.
3. For an unknown user, verify against the fixed dummy hash and return the same generic failure message.
4. Deny CLOSED or currently locked customers without disclosing the reason to the caller.
5. Verify the hash. Record consecutive failures safely; after five, set a lock for 15 minutes.
6. Ensure failed-count updates are not lost under concurrent attempts.
7. Define lock expiry so a temporary lock can clear; reset failures on successful authentication.
8. Create a session and audit SUCCESS only for valid authentication.
9. Audit FAILURE/LOCKED outcomes with nullable customer ID for unknown users.
10. Clear password arrays on every path.

**Test:** correct login, wrong password, unknown username, identical generic message, threshold boundary, lock duration/expiry, reset after success, closed customer and audit outcomes.

**Complete when:** T03–T05 pass and login failures cannot leak secrets.

**Next:** profile and password change.

### Step 14 — Implement profile viewing, permitted edits and password change

**Document:** F03, sections 15, 20–22.

**Work on:** CustomerService, AuthService, CustomerDAO.

1. Load the current customer's profile from session identity.
2. Display safe profile fields, never hashes or lockout internals.
3. Permit only explicitly documented profile fields; protect IDs, ownership, status and password-hash storage.
4. Validate edits and handle uniqueness conflicts if email is editable.
5. Require the current password again before changing it.
6. Enforce the new-password policy, hash it, replace the old hash, clear arrays and keep the valid session as specified.
7. Record the password-change audit event through a documented mechanism; do not invent an unsupported login_audit outcome.

**Test:** protected access, permitted versus forbidden edits, duplicate email, wrong current password, old password denied after change and new password accepted.

**Complete when:** customer profile and password workflows work without exposing internal security fields.

**Next:** Savings/Current account management.

---

## Phase 4 — Baseline banking (P1-M3 and P1-M4)

### Step 15 — Implement account creation, owned listing and balance enquiry

**Document:** F04–F05, F09, sections 5.1, 15–16; T06.

**Work on:** AccountService, AccountDAO, account models and validators.

1. Open Savings and Current accounts with zero balance and generated unique account numbers.
2. Link the owner from the active session.
3. Allow one customer to have multiple accounts.
4. List only owned accounts and show type, status and the correct balance meaning.
5. Use owner-scoped queries for individual balance/details requests.
6. Return a safe “Account not found” response for unauthorized lookups.
7. Keep all four account classes and type mappings represented, but do not offer incomplete Deposit/Loan creation. Their required atomic flows are steps 20–21.
8. Publish account-open events where used.

**Test:** multiple accounts, unique-number collision handling, another customer's lookup, missing account and CLOSED/FROZEN display versus operation rules.

**Complete when:** customers cannot read another customer's account data.

**Next:** deposits, the simplest financial unit of work.

### Step 16 — Implement ordinary deposits

**Document:** F06, section 18.3; T07, T13.

**Work on:** TransactionService, AccountDAO, TransactionDAO, domain policies.

1. Require an active session and valid positive amount.
2. Begin the transaction and lock the owned account.
3. Recheck ACTIVE status and Savings/Current eligibility.
4. Add the amount using Money.
5. Update the balance and insert one SUCCESS DEPOSIT ledger row with destination ID and destination balance-after snapshot.
6. Commit together, then publish/audit success.
7. On rejection or rollback, preserve original data and audit the attempt separately.

**Test:** valid deposit, zero/negative/over-scale/over-limit amount, non-owner, wrong type, frozen/closed account, balance overflow and ledger insert failure.

**Complete when:** a deposit cannot change the balance without its matching ledger row.

**Next:** withdrawals.

### Step 17 — Implement withdrawals

**Document:** F07, section 18.3; T08, T13.

1. Reuse the session, validation and transaction pattern.
2. Lock and re-read the owned ACTIVE Savings/Current account.
3. Check available funds after the lock.
4. Debit through domain behavior; insert one WITHDRAWAL ledger row with source balance-after snapshot.
5. Commit both or neither and audit the outcome separately.

**Test:** partial/exact-balance withdrawal, insufficient funds, invalid input, wrong ownership/type/status and ledger failure.

**Complete when:** balances never become negative and rejected withdrawals produce no SUCCESS ledger entry.

**Next:** atomic transfers.

---

## Phase 5 — Transfers and history (P1-M5)

### Step 18 — Implement atomic transfers and reconciliation

**Document:** F08, sections 12, 18.4; T09–T11, T14.

1. Require session identity, owned source, valid amount and destination account number.
2. Resolve destination ID without exposing private destination information.
3. Reject transfer to the same account.
4. Lock both existing account rows in ascending ID order.
5. Recheck source ownership, both statuses/types and available source funds under lock.
6. Debit source, credit destination and insert exactly one TRANSFER ledger row with both balance snapshots.
7. Commit once; publish success afterwards.
8. Exercise the bounded retry and same-reference behavior from step 9.
9. Reconcile uncertain commit outcomes rather than reapplying blindly.
10. Audit rejections/failures separately after rollback, and prevent post-commit audit failure from repeating the transfer.

**Mockito/service tests:** insufficient funds, self-transfer, bad destination, wrong status/type, ownership denial and collaborators receiving the same connection.

**Real MySQL tests:** success; forced failure after debit; credit update failure; ledger insert failure; parallel withdrawals; opposite-direction transfers using independent connections and coordinated threads.

**Complete when:** money is conserved, no partial transfer persists, one ledger row exists per committed operation, and overspending is impossible under concurrency.

**Next:** transaction history.

### Step 19 — Implement owned history, filters and stable pagination

**Document:** F10, section 18.6; T15.

1. Verify account ownership before querying history.
2. Include transactions where the account is source or destination without duplicating rows.
3. Show date/time, reference, type, amount, direction and status.
4. Implement optional date/type filters using parameters and whitelisted enums.
5. Use the documented half-open date range: start inclusive, end exclusive.
6. Order by `created_at DESC, transaction_id DESC`.
7. Validate page >= 0 and page size 1–50. Handle offset overflow and empty/out-of-range pages safely.
8. Derive CREDIT/DEBIT from the selected account's role; do not expose another customer's history.

**Test:** equal timestamps, first/last/empty pages, invalid size/page, date boundaries, type filters and ownership denial. Test stable pagination over a fixed dataset; document offset-pagination behavior if records are added between requests.

**Complete when:** T15 passes with no duplicates or omissions in the tested fixed history.

**Next:** dedicated Deposit-account lifecycle.

---

## Phase 6 — Deposit and Loan workflows (P1-M6)

### Step 20 — Implement term Deposit opening, maturity and settlement

**Document:** section 5.1, 18.5, 19; T16.

1. Validate principal, configured minimum and positive term months.
2. Require an owned ACTIVE Savings/Current funding account.
3. In one transaction, lock/recheck funding, debit it, create the Deposit account, insert deposit_details and create one TRANSFER ledger row.
4. Derive maturity_date from the documented Clock/date policy and term months.
5. Keep principal locked; refuse ordinary deposits, withdrawals and normal transfers involving this account.
6. On login and account listing, lazily mark owned ACTIVE deposits MATURED when maturity_date <= today.
7. Settle only MATURED deposits into an owned ACTIVE Savings/Current account.
8. Lock existing involved rows in ID order. Move principal, zero the deposit, close it and write one TRANSFER row atomically.
9. Recheck maturity/status under lock so a repeated settlement cannot pay twice.
10. Do not add interest engines or early-closure penalties; rates remain baseline metadata.

**Test:** opening funding failure rollback, early settlement refused, exact maturity boundary, successful settlement, non-owned funding/destination, closed/frozen endpoint, repeated settlement and ledger failure.

**Complete when:** T16 passes and opening/settlement cannot create or lose principal.

**Next:** Loan creation and repayment.

### Step 21 — Implement Loan creation and repayment

**Document:** sections 5.1, 15, 18.5; T17.

1. Implement the documented simplified creation flow with positive principal, tenure and start date.
2. Store loan_details and set account balance to outstanding principal.
3. Do not credit cash to another account and do not create a fictional disbursement ledger row.
4. For repayment require both an owned ACTIVE Loan and an owned ACTIVE Savings/Current funding account.
5. Lock both rows in ascending ID order.
6. Require amount <= loan outstanding and <= available funding balance.
7. Debit funding, reduce loan outstanding and insert one LOAN_REPAYMENT ledger row with snapshots in one transaction.
8. Close the Loan at zero. Publish LoanRepaid and, when appropriate, LoanClosed without duplicating the primary repayment audit.
9. Reject overpayment, repeat payment to a closed loan and all normal cash operations on Loan accounts.

**Test:** creation, partial repayment, exact repayment/closure, overpayment, insufficient funding, wrong ownership/type/status and rollback after any write.

**Complete when:** T17 passes and Loan balances are never treated as spendable funds.

**Next:** connect verified services to a complete terminal experience.

---

## Phase 7 — CLI completion and final verification (P1-M6 and P1-M7)

### Step 22 — Implement menus and startup wiring

**Document:** sections 9, 13, 20–22, 26.

**Work on:** BankingApplication, MainMenu, LoginMenu, CustomerMenu, ConsoleInput.

1. Compose configuration, connection factory, DAOs, policies, hasher, Clock, event publisher and services in the entry point.
2. Build welcome choices: Register, Login, Exit.
3. Build authenticated choices: view/edit profile, change password, create/list accounts, balance, deposit, withdraw, transfer, Deposit settlement, Loan repayment, filtered history and logout.
4. Route creation by type so Deposit/Loan use their dedicated flows.
5. Keep SQL and banking decisions out of menus.
6. Read password char arrays using `Console.readPassword()` where available.
7. If no secure console exists, fail clearly or use a deliberately documented, warned development-only fallback; never silently echo passwords.
8. Handle blank input, nonnumeric choices, malformed amounts and EOF without stack traces.
9. After three invalid attempts for one prompt, safely return to its menu.
10. Check expiry whenever blocked input returns, before treating that input as valid activity; recheck before protected execution.
11. Logout/timeout clear session state, audit once and return to the welcome menu.
12. Translate typed exceptions into safe user messages; keep sensitive internals out of terminal output and logs.
13. Show a receipt/reference only after a known successful commit; distinguish unresolved outcomes from definite failure.

**Test:** complete manual flows, invalid input, EOF, password input, idle while a prompt blocks, logout and attempts to reuse an expired session.

**Complete when:** every required service feature is reachable and the CLI remains usable after failures.

**Next:** run the complete security and integrity matrix.

### Step 23 — Complete automated and security verification

**Document:** sections 25, 35–38.

1. Run JUnit unit/service tests through Surefire.
2. Run JDBC/transaction integration tests through Failsafe against guarded `banking_test`.
3. Ensure integration tests fail clearly if the test database is unavailable; do not count skipped DB coverage as passing.
4. Complete T01–T18 and all twelve invariants.
5. Test SQL-injection payloads in text inputs; valid text is bound as data, and invalid input is rejected without changing SQL structure.
6. Test password change, lockout expiry, logout, profile edit rules, audit survival, post-commit audit failure and commit reconciliation in addition to the essential table.
7. Check logs and stored data for passwords, hashes in logs, credentials and full account numbers.
8. Inspect independent-connection concurrency results in the real database, not only Mockito invocation counts.
9. Review try-with-resources, transaction state restoration, exception paths, row-lock ordering and duplicate-reference handling.
10. Confirm all four account types have tests for both permitted and forbidden operations.

**Commands for the existing build layout:**

```text
mvn test
mvn verify -Pintegration
mvn clean package
java -jar target/banking-cli.jar
```

The source document groups tests under `mvn test`; this project's separate integration profile must be documented explicitly so database tests are not accidentally omitted. `mvn clean package` alone does not prove integration tests passed.

**Complete when:** automated results prove the security and integrity gates, with actual executed tests rather than placeholders.

**Next:** reproduce the application from a clean setup and prepare submission.

### Step 24 — Finish README, demo evidence and clean-checkout verification

**Document:** sections 26–27, 36–41.

1. Document JDK/Maven/MySQL versions, dependencies, schema setup and least-privilege users.
2. Explain private environment configuration without embedding real credentials.
3. Document both unit and real-MySQL integration test commands.
4. Document account rules, limits, timeout/lockout behavior, error messages and known limitations.
5. Record AI-assisted contributions in README and verify generated security/SQL/transaction code yourself.
6. Confirm Java source, MySQL schema, Maven configuration, resources, tests and documentation are in the submission.
7. Keep meaningful Git history and exclude generated build output and secrets.
8. From a clean checkout and empty database: apply schema, configure credentials, run tests, package and start the JAR.
9. Demonstrate registration, login, profile/password change, multiple accounts, deposit, withdrawal, transfer, history, Deposit maturity/settlement, Loan repayment, timeout and logout.
10. Demonstrate wrong password/lockout, unauthorized access, insufficient funds, invalid input, early settlement refusal, overpayment refusal and rollback after forced failure.
11. Be ready to explain hashing versus encryption, service authorization, prepared statements, locks, transaction rollback and audit independence.
12. Prepare for a small live change, such as adding a history filter or demonstrating rejection of a closed destination.

**Complete when:** another developer can reproduce setup and all acceptance gates from README alone.

---

## Essential test checklist

| ID  | Required scenario                                           | Implement/test with                                 |
|-----|-------------------------------------------------------------|-----------------------------------------------------|
| T01 | Registration stores a hash, not plaintext                   | Registration service + real MySQL                   |
| T02 | Duplicate username/email rejected without mutation          | Mockito/service + UNIQUE integration tests          |
| T03 | Correct password creates session                            | Auth service + real bcrypt                          |
| T04 | Wrong/unknown login gives same generic error and audit      | Auth service, dummy verification, audit integration |
| T05 | Five failures cause temporary lock and LOCKED audit         | Injected Clock + persistent counter tests           |
| T06 | Another customer's account cannot be accessed               | Service + owner-scoped MySQL queries                |
| T07 | Deposit balance and ledger agree                            | Real transaction test                               |
| T08 | Insufficient withdrawal leaves data unchanged               | Service + real transaction test                     |
| T09 | Transfer adjusts both accounts, one SUCCESS row             | Real transaction test                               |
| T10 | Failure after debit rolls back; failure audit survives      | Forced-failure real MySQL test                      |
| T11 | Self/closed/frozen transfer rejected                        | Policy + transaction tests                          |
| T12 | Idle expiry prevents protected action                       | Controllable Clock + CLI input-return check         |
| T13 | Malformed, negative and over-scale amounts rejected         | Money/validator + no-mutation tests                 |
| T14 | Concurrent withdrawals/opposite transfers remain safe       | Independent MySQL connections + coordinated threads |
| T15 | Pagination boundaries and ordering are correct              | Fixed dataset with equal timestamps                 |
| T16 | Early Deposit settlement denied; mature settlement succeeds | Clock + real atomic opening/settlement              |
| T17 | Partial/exact/overpayment Loan repayment rules              | Domain + service + real transaction tests           |
| T18 | SQL-injection text cannot change SQL behavior               | Validator + prepared-statement integration tests    |

## Requirement and invariant coverage

| Requirement                                  | Steps                          |
|----------------------------------------------|--------------------------------|
| F01 Registration                             | 5–8, 11                        |
| F02 Authentication                           | 7, 10, 12–13                   |
| F03 Profile/password changes                 | 14                             |
| F04–F05 Four account types/multiple accounts | 6, 8, 15, 20–21                |
| F06–F08 Deposit/withdraw/transfer            | 9–10, 16–18                    |
| F09 Balance enquiry                          | 15, 22                         |
| F10 History                                  | 19, 22                         |
| F11 Session lifecycle                        | 12–14, 22                      |
| F12 Database integrity                       | 3–9, 16–21, 23                 |
| F13 Audit                                    | 4, 10, 13, 16–18, 20–23        |
| F14 Testing                                  | Every step, completed in 23–24 |

| Invariant                                                 | Proof required                                                                                          |
|-----------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| INV-01 No plaintext/reversible passwords                  | Hash storage + real verification tests                                                                  |
| INV-02 No unauthorized account reads/changes              | Owner-scoped services/DAOs; source-only authorization for normal transfers, no destination data leakage |
| INV-03 Positive operation amounts, at most two decimals   | Money + validator + CHECK tests                                                                         |
| INV-04 No overdrawing                                     | Locked re-read + concurrent transaction tests                                                           |
| INV-05 Atomic transfers                                   | Forced failures at each financial write                                                                 |
| INV-06 SUCCESS ledger only for committed movement         | Ledger and balance share transaction                                                                    |
| INV-07 Loan not spendable, repayment bounded by principal | Loan operation matrix + partial/exact/overpayment tests                                                 |
| INV-08 Deposit funds locked until maturity                | Clock-driven settlement tests                                                                           |
| INV-09 Status restrictions                                | ACTIVE/FROZEN/MATURED/CLOSED matrix                                                                     |
| INV-10 Expired/logged-out session denied                  | Clock + session invalidation + CLI check                                                                |
| INV-11 Append-only, secret-free audit                     | Audit DAO/permissions/log inspection                                                                    |
| INV-12 Unique account numbers/references                  | Generator tests + MySQL UNIQUE constraints                                                              |

## Final acceptance gates

- No plaintext or reversibly encrypted password storage.
- No unauthorized customer account data access.
- No successful withdrawal above available funds.
- No partial transfer after any failure.
- No SQL concatenation of untrusted input.
- All four account types enforce their distinct rules.
- Failed financial attempts remain auditable after rollback.
- Login, lockout, timeout, logout and password change work.
- Unit, Mockito interaction, real JDBC, rollback and concurrency tests execute successfully.
- A clean checkout and empty MySQL database can reproduce the application using README.

## Your immediate next actions

1. **First:** verify Java/Maven and add the missing Mockito test dependencies.
2. **Second:** make a real JUnit test execute successfully.
3. **Third:** document baseline decisions, then create and verify the seven-table MySQL schema.
4. **Fourth:** implement configuration, logging and injectable connections.
5. **Fifth:** implement and unit-test `Money.java`.
6. Continue in numbered order; build registration/login before financial workflows and connect full menus after service behavior is verified.
