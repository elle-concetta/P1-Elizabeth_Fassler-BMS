# P1-Elizabeth_Fassler-BMS

Banking simulation using **Java 17, Maven 3.9+, JDBC, and MySQL 8.0.16+**.
Based on the supplied HLD / LLD / DDD document, version 2.0 (26 October 2026).

## Current status

The package folders, placeholder types, Maven build configuration,
resource files, SQL placeholders, and test folders are prepared. Registration,
authentication, account operations, persistence, and menus are **not implemented**.
The entry point prints a scaffold notice and exits. No database connection is opened.

This is not a real bank or production financial application. No web framework,
Spring Boot, ORM, GUI, payment network, or real money movement is included.

## Contents

- [Structure](#structure)
- [Build and run](#build-and-run)
- [Required libraries](#required-libraries)
- [Full application setup checklist](#full-application-setup-checklist--after-implementation)
- [Step 1 walkthrough](#step-1--maven-and-first-test-walkthrough)
- [Demo](#demo)
- [Planned functionality](#planned-functionality)
- [Configuration and database](#configuration-and-database--future-implementation)
- [Project document reference](#project-document-reference)
    - [Purpose, scope, roles and requirements](#purpose-scope-roles-and-requirements)
    - [Architecture and package responsibilities](#architecture-and-package-responsibilities)
    - [Class inventory and contracts](#class-inventory-and-contracts)
    - [Database reference design](#database-reference-design)
    - [Detailed workflows](#detailed-workflows)
    - [Lifecycle, validation, errors and observability](#lifecycle-validation-errors-and-observability)
    - [Domain-driven design](#domain-driven-design)
    - [Testing and traceability](#testing-and-traceability)
    - [Milestones, evaluation and submission](#milestones-evaluation-and-submission)
- [Next steps](#next-steps)

## Structure

```text
(project root)
├── .idea/
│   ├── dataSources/
│   │   └── data_sources_history.xml
│   ├── .gitignore
│   ├── compiler.xml
│   ├── encodings.xml
│   ├── jarRepositories.xml
│   ├── misc.xml
│   └── workspace.xml
├── .mvn/                         
├── .gitignore
├── pom.xml
├── README.md
├── IMPLEMENTATION_PLAN.md
├── docs/decisions.md
├── sql/
│   ├── mysql/schema.sql          
│   └── sample-data.sql           
├── src/main/resources/
│   ├── app.properties
│   └── logback.xml
├── src/main/java/com/example/banking/
│   ├── app/                      entry point, menus, input
│   ├── model/                    domain types and enums
│   │   └── policy/               eligibility, transfer, password, lockout
│   ├── service/                  use cases and transaction boundaries
│   ├── dao/                      repository interfaces
│   │   └── jdbc/                 JDBC implementation placeholders
│   ├── security/                 hashing and session contracts
│   ├── validation/               input validation placeholders
│   ├── exception/                typed failures
│   └── util/                     configuration, connections, transaction helper
└── src/test/java/com/example/banking/
    ├── unit/
    ├── service/
    └── integration/
```

The Maven files are directly at the project root, with no extra enclosing folder.
This layout follows the supplied IntelliJ screenshot. Open the project or its
`pom.xml` in IntelliJ, select an installed JDK 17, and import the Maven project.
The `.idea` files are portable starter settings; IntelliJ will update local workspace
settings. `External Libraries` and `Scratches and Consoles` are IDE views, not
physical project directories. The empty `.mvn` folder does not provide `mvnw`;
use an installed Maven.

## Build and run

Install JDK 17+ and Maven 3.9+, then run from this folder:

```bash
mvn test
mvn clean package
java -jar target/banking-cli.jar
```

The build commands above are configured but have not been executed here because
Java and Maven were not available when the scaffold was created.
The test directory is empty; no test implementations are included.

For future database integration tests named `*IT.java`:

```bash
mvn verify -Pintegration
```

Implement test database safety guards before writing tests that mutate data.

## Required libraries

| Library               | Purpose                                             | Current status                          |
|-----------------------|-----------------------------------------------------|-----------------------------------------|
| MySQL Connector/J     | JDBC connections and MySQL operations               | Declared in `pom.xml`                   |
| JUnit Jupiter         | Automated tests                                     | Declared; no tests written yet          |
| Mockito Core          | Mock DAOs and JDBC objects for isolated tests       | To be added in Step 1                   |
| Mockito JUnit Jupiter | Initialize Mockito mocks in JUnit 5                 | To be added in Step 1                   |
| SLF4J API             | Logging interface                                   | Declared                                |
| Logback Classic       | Logging implementation, configured by `logback.xml` | Declared; configuration scaffold exists |
| bcrypt                | Salted adaptive password hashing and verification   | Declared; implementation pending        |

### Dependency coordinates and versions

These are the versions currently pinned in `pom.xml`, not a claim that they are
the newest releases. The source document specifies the library families rather
than these exact versions.
t` | 0.10.2 | Application |
| `org.slf4j:slf4j-api` | 2.0
| Maven coordinate | Version | Scope |
|---|---|---|
| `com.mysql:mysql-connector-j` | 9.2.0 | Application |
| `at.favre.lib:bcryp.16 | Application |
| `ch.qos.logback:logback-classic` | 1.5.16 | Application |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | Test |
| `org.mockito:mockito-core` | 5.14.2 proposed in Step 1; not yet added | Test |
| `org.mockito:mockito-junit-jupiter` | 5.14.2 proposed in Step 1; not yet added | Test |

Maven Compiler 3.13.0 targets Java 17; Surefire 3.5.2 runs unit/service tests;
Shade 3.6.0 produces the executable JAR; Failsafe 3.5.2 runs integration tests
when the `integration` profile is selected. The configured main class is
`com.example.banking.app.BankingApplication`.

Passwords must be hashed, not reversibly encrypted. Mockito connection tests do
not contact MySQL; real database integration tests are also required to prove
SQL constraints, transactions, rollback, and locking.

## Step 1 — Maven and first test walkthrough

The full build order is in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).
Start here before implementing menus or banking operations. MySQL does not need
to be running for this first setup test.

### 1. Check Java and Maven

Run from the folder containing `pom.xml`:

```bash
java -version
mvn -version
```

Use JDK 17 and Maven 3.9+. Confirm that Maven also reports Java 17.
In IntelliJ, open **File → Project Structure → Project** and select JDK 17.
Under **Settings → Build, Execution, Deployment → Build Tools → Maven**, select
an installed Maven or IntelliJ's bundled Maven.

If the terminal cannot find `mvn`, the Maven tool window can still run goals with
bundled Maven. Downloading a JDK inside IntelliJ does not necessarily put `java`
on the terminal's PATH; configure the local JDK/PATH if using terminal commands.

### 2. Add Mockito test dependencies

The existing `pom.xml` already includes JUnit and the application libraries.
Add the following inside its existing `<dependencies>` block, before
`</dependencies>`. These are instructions; they have not yet been applied:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.14.2</version>
    <scope>test</scope>
</dependency>

<dependency>
<groupId>org.mockito</groupId>
<artifactId>mockito-junit-jupiter</artifactId>
<version>5.14.2</version>
<scope>test</scope>
</dependency>
```

Reload the Maven project after saving. Keep the existing dependencies and plugins.
Test scope keeps Mockito out of the packaged application's runtime dependencies.

### 3. Create the first JUnit/Mockito test

Create this Java file:

```text
src/test/java/com/example/banking/unit/ConnectionMockTest.java
```

In IntelliJ, right-click the `unit` package and choose **New → Java Class**.
Enter `ConnectionMockTest`; the IDE supplies the `.java` extension.

```java
package com.example.banking.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionMockTest {

    @Mock
    private Connection connection;

    @Test
    void shouldUseMockDatabaseConnection() throws SQLException {
        // Arrange: define the simulated connection response.
        when(connection.isValid(2)).thenReturn(true);

        // Act: call the simulated connection.
        boolean valid = connection.isValid(2);

        // Assert: check the result and expected interaction.
        assertTrue(valid);
        verify(connection).isValid(2);
    }
}
```

`@Mock` creates a simulated JDBC connection, `when` defines its response,
`assertTrue` checks the result, and `verify` checks the call. The extension
initializes the mock. This test confirms the JUnit/Mockito setup only; it does
not yet test application behavior or a real database connection.

### 4. Run tests, package, and launch

```bash
mvn test
mvn clean package
java -jar target/banking-cli.jar
```

After adding the test, expect a nonzero executed test count, zero failures/errors,
and `BUILD SUCCESS`. Running the commands before adding a test does not prove
the test runner executed a test.

In IntelliJ's Maven tool window, **Lifecycle → test** runs the tests and
**Lifecycle → package** packages the application. The `target` directory is
generated automatically; do not create it manually.

### Step 1 completion checklist

- [ ] JDK 17 is selected and Maven 3.9+ is available.
- [ ] Maven resolves the existing libraries and both Mockito dependencies.
- [ ] `ConnectionMockTest.java` exists and Maven executes it successfully.
- [ ] `mvn clean package` creates `target/banking-cli.jar`.
- [ ] Launching the JAR prints the scaffold notice.

These boxes remain unchecked until the commands have actually succeeded.

## Demo

### Current Demo

This is the only application behavior currently available. With JDK/Maven
installed, package and run:

```bash
mvn clean package
java -jar target/banking-cli.jar
```

Expected application output, based on the entry point:

```text
Secure Banking CLI — scaffold only.
Banking features are not implemented. See README.md and the supplied design.
```

No database credentials are needed. This output is not evidence of implemented
banking functionality, and the build has not been verified in this workspace.
After completing Step 1, also demonstrate the passing test with `mvn test`.

### Planned final banking demo — not yet implemented

Use synthetic customers and demonstration accounts only. Enter passwords
privately; do not include passwords, hashes, or credentials in screenshots,
recordings, receipts, or documentation.

| Order | Demonstration                                                  | Expected result after implementation                                   |
|-------|----------------------------------------------------------------|------------------------------------------------------------------------|
| 1     | Apply schema to a clean demo database and launch               | Reproducible setup; welcome menu                                       |
| 2     | Register a customer; try a duplicate username/email            | Valid registration succeeds; duplicate is rejected                     |
| 3     | Try wrong/unknown credentials, then log in correctly           | Same generic failure message; valid login creates a session            |
| 4     | View/edit permitted profile fields; change password            | Current password required; new password works                          |
| 5     | Open Savings and Current accounts and list them                | Unique numbers; only owned accounts shown                              |
| 6     | Deposit 500.00 into Savings; withdraw 100.00                   | Savings balance 400.00; one ledger row per committed operation         |
| 7     | Transfer 150.00 from Savings to Current                        | Savings 250.00; Current 150.00; one TRANSFER row                       |
| 8     | Attempt a 300.00 Savings withdrawal                            | Rejected; balances and successful ledger unchanged                     |
| 9     | View history, date/type filters, and pages                     | References, directions, statuses, stable ordering                      |
| 10    | Open a Deposit funded from owned Savings/Current               | Funding debited atomically; principal locked                           |
| 11    | Attempt early settlement, then settle a mature test Deposit    | Early release refused; mature principal transferred and Deposit closed |
| 12    | Create a Loan; make partial, excessive, and exact repayments   | Debt reduced; overpayment refused; exact repayment closes Loan         |
| 13    | Try accessing another customer's account details/history       | Access denied without private data disclosure                          |
| 14    | Demonstrate five failed logins and lock expiry                 | Temporary 15-minute lock; safe authentication audit                    |
| 15    | Leave an authenticated prompt idle, then submit input          | Expired session denied before action; TIMEOUT audited                  |
| 16    | Log in again and log out                                       | Session cleared; LOGOUT audited                                        |
| 17    | Run rollback/concurrency tests and inspect safe audit evidence | No partial transfers or overspending; failed attempts survive rollback |

For Deposit maturity and lockout/timeout boundaries, use controlled-clock tests
rather than waiting months or modifying real customer records. Use isolated test
fixtures for fault injection and frozen/closed-account cases. No customer-facing
admin menu or intentional failure switch is required.

Run the final automated evidence separately:

```bash
mvn test
mvn verify -Pintegration
```

Integration tests must target guarded `banking_test`, never the application's
database. Record actual test outcomes when implemented; do not mark this planned
demo as completed before the corresponding features and tests work.

## Planned functionality

- Registration, bcrypt password hashing, login, temporary lockout.
- Profile management and password changes with current-password verification.
- Multiple owned Savings, Current, Deposit, and Loan accounts.
- Deposits, withdrawals, atomic transfers, balances, filtered paginated history.
- Funded term deposits, maturity and settlement, Loan repayment.
- Inactivity timeout, logout, and append-only authentication/financial audit.

### Account rules

| Type    | Balance meaning            | Deposit operation                                         | Withdrawal                       | Ordinary transfer           | Specialized rule                                                                                                          |
|---------|----------------------------|-----------------------------------------------------------|----------------------------------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| Savings | Spendable cash             | Yes, owned ACTIVE account                                 | Yes, funds >= amount             | Eligible source/destination | Minimum balance 0 by default                                                                                              |
| Current | Spendable cash             | Yes, owned ACTIVE account                                 | Yes, funds >= amount             | Eligible source/destination | Overdraft limit 0 in baseline                                                                                             |
| Deposit | Locked principal           | Opening funding only, from owned eligible Savings/Current | Only through maturity settlement | Not an ordinary endpoint    | Fixed maturity date; settle principal into owned eligible Savings/Current, zero balance and close                         |
| Loan    | Outstanding principal owed | No ordinary deposit                                       | Never; not spendable cash        | Not an ordinary endpoint    | Simplified creation without disbursement; repay from owned eligible Savings/Current; refuse overpayment and close at zero |

Ordinary transfers require an owned source; an eligible destination may belong to
another customer without granting access to its private balance/history.
Deposit funding/settlement use dedicated workflows, not ordinary transfer eligibility.
Loan creation does not disburse money. No interest engine is in scope.

## Full application setup checklist — after implementation

This is the operational setup required by source sections 8 and 26. It applies
**after** the schema, configuration loader, persistence and banking services have
been implemented. At present only the scaffold build/run instructions apply.

1. **Verify prerequisites.** Use JDK 17, Maven 3.9+ and a running MySQL 8.0.16+
   server with InnoDB. Import the root `pom.xml`; do not create a nested project.
2. **Create isolated databases.** Create `banking` for application/demo data and
   `banking_test` for automated tests. Do not run integration tests against the
   application database.
3. **Separate setup and runtime privileges.** An authorized setup account creates
   tables and grants privileges. A non-root runtime principal has only the
   necessary DML privileges on `banking`; a separate test principal targets
   `banking_test`. Restrict audit tables to required append/read access rather
   than granting blanket UPDATE/DELETE. Any test reset uses an explicitly guarded
   setup/cleanup connection with only the test privileges it needs.
4. **Apply the implemented schema to each database.** Once
   `sql/mysql/schema.sql` contains the reviewed DDL, select the intended database
   explicitly and run the script. For example, in an authenticated MySQL client:

   ```sql
   USE banking;
    SOURCE sql/mysql/schema.sql;
   ```

   Run the corresponding setup separately for `banking_test`. The path is
   relative to the client's working directory; use a verified absolute path if
   needed. Do not rerun CREATE TABLE statements over existing data without an
   explicit migration/reset decision.
5. **Configure credentials privately.** Provide `DB_URL`, `DB_USERNAME` and
   `DB_PASSWORD` to the Java process. A local non-secret JDBC URL shape is
   `jdbc:mysql://127.0.0.1:3306/banking`. Provide corresponding `TEST_DB_*` values
   pointing to `banking_test` only for test execution. Never put passwords into
   JDBC URLs, README examples, shared run configurations or tracked files.
6. **Review properties.** Confirm timeout, lockout, hashing cost, amount limit and
   history-size defaults in the configuration table. Application code must reject
   missing/invalid required settings rather than silently selecting an unsafe
   database or insecure fallback.
7. **Run both test layers.** Run `mvn test`, then
   `mvn verify -Pintegration`. Verify actual executed counts and inspect
   `target/surefire-reports/` and `target/failsafe-reports/`. Integration tests
   must check database identity before creating fixtures or deleting test data.
8. **Package and launch.** Run `mvn clean package` and
   `java -jar target/banking-cli.jar`. The same private configuration must be
   available to the launched process. Packaging is not a substitute for the
   integration verification command.
9. **Create demo customers through registration.** Use synthetic data and freshly
   chosen private passwords, then follow the demo section. No real customer data,
   reusable sample passwords or production-like financial claims.

These steps are not evidence that database setup, permissions, application
configuration loading or tests have already been completed.

## Configuration and database — future implementation

Non-secret defaults are in `src/main/resources/app.properties`.
Read `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from private environment variables
when implementing `AppConfig`. Never store credentials in resources or Git.

Create isolated `banking` and `banking_test` databases with separate principals.
Integration tests should use `TEST_DB_URL`, `TEST_DB_USERNAME`, and
`TEST_DB_PASSWORD`, rejecting missing configuration or application database targets.
No credentials are needed to run the current scaffold.

The seven planned tables are `customers`, `accounts`, `deposit_details`,
`loan_details`, `transactions`, `login_audit`, and `transaction_audit`.
The SQL files deliberately contain no executable schema yet.

### Resource file responsibilities

- `src/main/resources/app.properties` is a non-secret settings file, **not Java**.
- `src/main/resources/logback.xml` is logging configuration, **not Java**.
- `src/main/java/com/example/banking/app/` is a Java package directory containing
  the `.java` entry point, menus and console-input classes.
- `sql/mysql/schema.sql` is MySQL DDL. `sql/sample-data.sql` is optional synthetic
  seed data; the current files remain placeholders.
- `README.md` documents setup, usage, design and AI assistance.
  `IMPLEMENTATION_PLAN.md` gives the work order; `docs/decisions.md` records choices.

## Implementation constraints

- Use `BigDecimal`/`DECIMAL(19,2)`, never floating point, for money.
- Reject nonpositive operation amounts, more than two fractional digits, and limits.
- Derive identity from an active session; authorize every protected service call.
- Use prepared statements; keep SQL in JDBC DAOs.
- Services coordinate one connection/transaction; DAOs never commit independently.
- Lock affected existing accounts in ascending ID order and recheck rules under lock.
- Commit balances and one successful ledger row together; roll back on failure.
- Keep failed attempts in independently committed audit, not successful ledger rows.
- Preserve one operation reference across bounded retries; never blindly repeat an uncertain commit.
- No passwords, hashes, credentials, or full account identifiers in logs.
- Session timeout is five minutes by default, checked when blocked CLI input returns.

## Project document reference

The following records the project details from **P1 — Secure Banking CLI:
High-Level Design, Low-Level Design and Domain-Driven Design**, version **2.0,
26 October 2026**, adapted to this project's **MySQL-only** selection.
These are **design requirements, not implemented capabilities**. Code fragments
and SQL in this reference do not change the placeholder source or schema files.
The original PDF remains the reference for its visual UML diagrams.

### Document control and conventions

The project is an individual P1 assignment for training Weeks 2–3 and a foundation
for P2. Version 2.0 consolidates and supersedes the original version 1.0 HLD/DLD/UML
pack; the detailed design is called LLD rather than DLD.

Version 2.0 clarifies these important changes:

1. Loan balance means nonnegative outstanding principal; type-specific terms live
   in `loan_details` and `deposit_details`.
2. Persisted account statuses include MATURED; CREATED is in-memory only.
3. A business money movement has one ledger row with balance-after snapshots.
4. Failed financial attempts belong in separate `transaction_audit` records.
5. Deposit and Loan workflows have explicit, testable baseline rules.
6. Authentication includes failure counting, temporary lockout, generic errors
   and dummy-hash verification.
7. Transactions use ordered locks, explicit isolation, timeouts and bounded retries.
8. DDD language, contexts, aggregates, policies and events are included.
9. Blocking CLI input must be checked for session expiry when input returns.

Abbreviations: PK = primary key; FK = foreign key; UQ = unique; VO = value object;
DAO = Data Access Object; DTO = Data Transfer Object; HLD = High-Level Design;
LLD = Low-Level Design; DDD = Domain-Driven Design; CLI = command-line interface.
F01–F14 are design requirements corresponding to brief IDs P1-01–P1-14.
Diagrams are conceptual; implementation refinements belong in
[docs/decisions.md](docs/decisions.md).

### Purpose, scope, roles and requirements

**Source sections 1–5 and 9.** Build a secure, menu-driven Java terminal
application where customers register, authenticate, manage multiple accounts,
perform eligible operations, review history and end sessions safely.
The database is the source of truth; identity comes from the session; balance
changes and ledger writes commit together; layers remain independently testable.

| In scope                                        | Out of scope                                               |
|-------------------------------------------------|------------------------------------------------------------|
| Registration, profile and password changes      | Real money, payment networks, KYC/regulatory workflows     |
| Login, hashing, lockout, sessions and logout    | Production identity provider, MFA implementation           |
| Savings, Current, Deposit and Loan accounts     | Interest engines, loan origination/disbursement            |
| Deposit, withdrawal, atomic transfer, repayment | Internet-facing API, distributed services, fraud detection |
| Balance, filtered/paginated history, audit      | GUI, web framework, Spring Boot, React                     |
| JDBC, automated tests, reproducible setup       | Production banking certification/hardening                 |

| Role              | Responsibility                                                                                                                                 |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Customer          | Register/login; manage own permitted profile fields; open accounts; transact on owned eligible sources; view own data; change password; logout |
| Application       | Validate, authorize, hash/verify, manage expiry, coordinate transactions and write ledger/audit                                                |
| Database          | Enforce keys/constraints and provide persistence, locking, isolation and atomic commit                                                         |
| Trainer/evaluator | Inspect code/schema, run tests and observe security/rollback demonstrations; no customer-facing admin privilege                                |

#### Functional requirements

| ID / brief  | Requirement       | Acceptance                                                                         |
|-------------|-------------------|------------------------------------------------------------------------------------|
| F01 / P1-01 | Registration      | Unique username/email; validated fields; password policy; only encoded hash stored |
| F02 / P1-02 | Authentication    | Valid credentials create session; invalid credentials give generic audited denial  |
| F03 / P1-03 | Profile           | View/update permitted fields; password change verifies current password            |
| F04 / P1-04 | Account creation  | All four types; generated unique account number; owner linked                      |
| F05 / P1-05 | Multiple accounts | One customer may own many; listings are owner-scoped                               |
| F06 / P1-06 | Deposit           | Positive amount; owned ACTIVE eligible account; balance and ledger agree           |
| F07 / P1-07 | Withdrawal        | Sufficient funds after row lock and re-read                                        |
| F08 / P1-08 | Transfer          | Debit, credit and ledger are atomic; any failed step rolls back                    |
| F09 / P1-09 | Balance enquiry   | Owned account details and balance only                                             |
| F10 / P1-10 | History           | Dated, referenced, status-bearing records with stable pagination                   |
| F11 / P1-11 | Sessions          | Idle expiry, invalidation and explicit logout                                      |
| F12 / P1-12 | Integrity         | Keys, constraints, prepared statements, DECIMAL money and JDBC transactions        |
| F13 / P1-13 | Audit             | Login/financial outcomes with reference, timestamp and status                      |
| F14 / P1-14 | Testing           | Automated success, bad input, funds, authorization, rollback and concurrency cases |

#### Non-functional requirements

| Attribute       | Target                                                                                                           |
|-----------------|------------------------------------------------------------------------------------------------------------------|
| Security        | Salted adaptive hashes; no plaintext/reversible passwords; service authorization; prepared SQL; external secrets |
| Integrity       | BigDecimal/DECIMAL(19,2); explicit precision rules; locked financial transactions                                |
| Maintainability | Layered packages; no SQL in menus/services; no banking rules in presentation                                     |
| Reliability     | try-with-resources, explicit rollback, typed exceptions, safe connection-loss handling                           |
| Testability     | Injected collaborators and Clock; fake/Mockito service tests; separate real test database                        |
| Usability       | Numbered menus; invalid-input/EOF recovery; safe messages                                                        |
| Portability     | Standard JDBC; dialect differences documented; only MySQL implemented here                                       |
| Observability   | Structured, correlated logs with no passwords, hashes, credentials or full account data                          |

#### Money and account rules in detail

- A customer has one unique ID; each account has exactly one owner.
- Generate account numbers/references under controlled code and enforce database uniqueness.
- Money is immutable BigDecimal at scale 2. Reject inputs with more than two
  decimal places rather than rounding; use `RoundingMode.UNNECESSARY`.
- Operation amounts must be positive and <= 1,000,000.00 by default. Zero is a
  valid account balance, not a valid financial operation amount.
- Savings minimum balance is zero by default; Current overdraft limit is zero.
- Deposit opening requires an owned Savings/Current funding account and positive
  principal/term. Ordinary top-ups and normal transfer endpoints are forbidden.
  Early closure is refused; mature principal settles into owned Savings/Current
  and the Deposit closes.
- Loan creation records principal owed without disbursing cash. No ordinary
  deposit, withdrawal or transfer is allowed. Repayment reduces debt from owned
  Savings/Current funds; overpayment is refused and zero outstanding closes it.
- Implement Savings/Current first, then complete Deposit/Loan before evaluation.
- Session inactivity and database query/lock timeouts are different controls.

#### Use-case alternatives

| Use case         | Successful flow                                         | Important alternatives                                         |
|------------------|---------------------------------------------------------|----------------------------------------------------------------|
| Register         | Validate → hash → save                                  | Duplicate fields, invalid input, weak password                 |
| Login            | Verify → create session → customer menu                 | Unknown/wrong credentials, locked/closed customer              |
| Open account     | Select type → validate terms → generate number → save   | Invalid type, missing term/principal, invalid funding          |
| Deposit/withdraw | Select owned account → validate → transact → receipt    | Non-owner, inactive/wrong type, bad amount, insufficient funds |
| Transfer         | Resolve → ordered locks → debit/credit/ledger → commit  | Self-transfer, bad endpoint, insufficient funds, write failure |
| Repay            | Select owned Loan/funding → debit funding → reduce debt | Closed Loan, non-owner, overpayment                            |
| History          | Select owned account → filter → paginate                | Unauthorized account, invalid/out-of-range page                |
| Logout/timeout   | Clear session → audit → welcome                         | Idle expiry detected when blocked input returns                |

### Architecture and package responsibilities

**Source sections 6–8 and 13.** Deployment is a single Java process on a
workstation talking through JDBC to local MySQL. Use a least-privilege runtime
user, private environment credentials and a separate `banking_test` schema.
This logical deployment is not production hardening.

```text
Customer terminal
    → app: menus and input
    → service: authorization and use-case/transaction orchestration
    → model / policies: invariants and behavior
    → dao interfaces → dao.jdbc: prepared SQL and row mapping
    → MySQL: persistent constraints, locks, balances and ledger

Cross-cutting: security, validation, exceptions, configuration, logging and audit
```

| Decision | Rule and consequence                                                                                            |
|----------|-----------------------------------------------------------------------------------------------------------------|
| AD-1     | Plain Java + JDBC, no application framework                                                                     |
| AD-2     | DAO interfaces separate SQL from use cases and enable isolated tests                                            |
| AD-3     | Services own transactions; participating DAOs receive the same Connection                                       |
| AD-4     | Pessimistic `FOR UPDATE` locks in ascending account-ID order                                                    |
| AD-5     | Explicit READ COMMITTED; re-read balances after locking, rather than relying on MySQL's default REPEATABLE READ |
| AD-6     | One ledger row per business transaction with optional endpoints and balance snapshots                           |
| AD-7     | Separate append-only audit; failed attempts never appear as SUCCESS ledger rows                                 |
| AD-8     | PasswordHasher abstraction; bcrypt cost 12 baseline; original document permits Argon2id/PBKDF2 alternatives     |
| AD-9     | In-memory sessions and injected Clock                                                                           |
| AD-10    | Properties for non-secrets; environment variables for secrets                                                   |

| Package                  | Owns                                                                | Must not do                                               |
|--------------------------|---------------------------------------------------------------------|-----------------------------------------------------------|
| `app`                    | Startup, menus, input parsing, rendering, boundary requests/DTOs    | SQL or financial business rules                           |
| `model` / `model.policy` | State, VOs, enums, invariants, pure policies                        | Console I/O or JDBC                                       |
| `service`                | Use cases, authorization, transaction boundaries, event publication | Build SQL or read terminal input                          |
| `dao` / `dao.jdbc`       | Repository contracts, prepared SQL, ResultSet mapping               | Independently commit caller work or decide banking policy |
| `security`               | Hashing and session lifecycle                                       | Log secrets                                               |
| `validation`             | Field/input validation                                              | Access the database                                       |
| `exception`              | Typed, user-safe failures                                           | Expose SQL/internal details to users                      |
| `util`                   | Config, connections, transaction helper, references, logging        | Own banking rules                                         |

#### Diagram guide

The original document's 13 visual figures cover: (1) layers, (2) components,
(3) single-workstation deployment, (4) customer use cases, (5) domain classes,
(6) entity relationships, (7) login sequence, (8) transfer sequence/transaction
boundary, (9) transfer decisions, (10) account lifecycle, (11) session activity,
(12) bounded contexts and (13) aggregates. Their responsibilities, relationships,
states and execution order are described in this README; the original figures
are not reproduced as images here.

### Class inventory and contracts

**Source sections 13–16 and 31–34.** The following is the target inventory, not a
claim that all classes are implemented or that all later DDD types already exist.

| Package             | Types                                                                                                                                                                                          |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app`               | BankingApplication, MainMenu, LoginMenu, CustomerMenu, ConsoleInput                                                                                                                            |
| `model`             | Customer, abstract Account, SavingsAccount, CurrentAccount, DepositAccount, LoanAccount, BankTransaction, LoginAudit, TransactionAudit, Money, AccountNumber                                   |
| `model` enums       | AccountType, AccountStatus, CustomerStatus, TransactionType, TransactionStatus                                                                                                                 |
| `model.policy`      | AccountEligibilityPolicy, TransferPolicy, PasswordPolicy, LockoutPolicy                                                                                                                        |
| `service`           | AuthService, CustomerService, AccountService, TransactionService, SessionService                                                                                                               |
| `dao`               | CustomerDAO, AccountDAO, TransactionDAO, LoginAuditDAO, TransactionAuditDAO                                                                                                                    |
| `dao.jdbc`          | JdbcCustomerDAO, JdbcAccountDAO, JdbcTransactionDAO, JdbcLoginAuditDAO, JdbcTransactionAuditDAO                                                                                                |
| `security`          | PasswordHasher, BcryptPasswordHasher, SessionContext, SessionManager                                                                                                                           |
| `validation`        | InputValidator, CustomerValidator, AccountValidator, TransactionValidator                                                                                                                      |
| `exception`         | BankingException, ValidationException, AuthenticationException, AuthorizationException, InsufficientFundsException, AccountNotEligibleException, PersistenceException, SessionExpiredException |
| `util`              | AppConfig, ConnectionFactory, JdbcTransaction, ReferenceGenerator, LoggerFactory                                                                                                               |
| Later DDD additions | TransactionReference, Username, Email, PasswordHash, DepositTerms, LoanTerms, AuditOutcome; AccountNumberGenerator responsibility; domain events and EventPublisher                            |

Account holds shared identity, owner, status and invariants; use subclassing only
for genuinely different behavior. No public balance setter is permitted.
Money supplies `add`, `subtract`, `isPositive`, `compareTo`, value equality and
immutable results. BankTransaction is immutable and created through factories
that enforce legal endpoint shape. PasswordHasher exposes
`String hash(char[] raw)` and `boolean verify(char[] raw, String encoded)`;
callers clear password arrays after use. Audit entities are append-only.

#### Service dependencies

| Service            | Collaborators                                                                                                     |
|--------------------|-------------------------------------------------------------------------------------------------------------------|
| AuthService        | CustomerDAO, LoginAuditDAO, PasswordHasher, SessionManager, CustomerValidator; password/lockout policy            |
| CustomerService    | CustomerDAO, CustomerValidator                                                                                    |
| AccountService     | AccountDAO, CustomerDAO, AccountValidator, ReferenceGenerator; eligibility policy                                 |
| TransactionService | AccountDAO, TransactionDAO, TransactionAuditDAO, JdbcTransaction, TransactionValidator, ReferenceGenerator, Clock |
| SessionService     | SessionManager, Clock                                                                                             |

#### Illustrative service contracts

| Method                                                      | Preconditions                                                 | Result                                                                |
|-------------------------------------------------------------|---------------------------------------------------------------|-----------------------------------------------------------------------|
| `register(request)`                                         | Valid unique fields and compliant password                    | ACTIVE customer with hash; validation failure otherwise               |
| `authenticate(username, password)`                          | Nonblank input; DB available                                  | Session on success; generic audited AuthenticationException otherwise |
| `changePassword(session, current, new)`                     | Active session; current password verifies; valid new password | Hash replaced; audit event; session retained                          |
| Profile view/update                                         | Active session; allowed fields only                           | Current customer's safe profile                                       |
| `openAccount(customerId, type, params)`                     | Session-derived identity and valid type-specific parameters   | Unique owned account with correct initial balance/terms               |
| `getOwnedAccounts(customerId)`                              | Identity derived from session                                 | Only owned accounts                                                   |
| `deposit(customerId, accountId, amount)`                    | Owned ACTIVE Savings/Current; valid amount                    | Balance and DEPOSIT row committed together                            |
| `withdraw(customerId, accountId, amount)`                   | Same eligibility; enough funds under lock                     | Debit and WITHDRAWAL row, or no change                                |
| `transfer(customerId, sourceId, destinationNumber, amount)` | Owned source, distinct eligible ACTIVE endpoints              | Atomic debit/credit/TRANSFER or full rollback                         |
| `repayLoan(customerId, fundingId, loanId, amount)`          | Both owned; eligible funding; ACTIVE Loan; repayment <= debt  | Debit funding/reduce Loan; LOAN_REPAYMENT; close at zero              |
| `history(customerId, accountId, filter, page)`              | Owned account; valid page/size                                | Ordered rows with CREDIT/DEBIT direction                              |
| `isExpired(session, now)` / `requireActive(session)`        | Session timestamps and configured idle duration               | Expire/invalidate/audit, or refresh valid activity                    |

#### DAO contracts

All user values use PreparedStatement parameters.

| DAO                 | Operations                                                                                                                               |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| CustomerDAO         | Insert; find by ID/username; username/email existence; profile/hash updates; failed-login increment/reset; locked-until update           |
| AccountDAO          | Insert; owned lookup by ID; lookup by number; list by customer; ordered `lockForUpdate`; balance/status updates; type-detail persistence |
| TransactionDAO      | Insert on caller connection; account history/filter/page; find by reference                                                              |
| LoginAuditDAO       | Append customer ID if known, attempted username and outcome using own short transaction                                                  |
| TransactionAuditDAO | Append customer, reference, event type, outcome and failure code outside the money transaction                                           |

### Database reference design

**Source sections 11 and 17.** Seven tables are required. Customers have zero or
many accounts, each account exactly one customer. Accounts have zero or many
source/destination transactions and zero or one relevant type-detail row.
Known customers have login-audit rows; unknown login attempts have null customer
ID. Transaction audit links logically by reference, not a mandatory ledger FK:
failed attempts have no committed ledger row.

#### Complete MySQL reference DDL

The following reproduces the reference schema's columns, constraints and indexes
with formatting changes. It is documentation only; `sql/mysql/schema.sql` remains
a placeholder until the schema implementation step. Use MySQL 8.0.16+ with
InnoDB and verify the storage engine and CHECK enforcement when implementing.

<details>
<summary>Expand the seven-table MySQL schema</summary>

```sql
CREATE TABLE customers (
                           customer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                           full_name VARCHAR(120) NOT NULL,
                           email VARCHAR(254) NOT NULL UNIQUE,
                           phone VARCHAR(30),
                           username VARCHAR(80) NOT NULL UNIQUE,
                           password_hash VARCHAR(255) NOT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                           failed_login_count INT NOT NULL DEFAULT 0,
                           locked_until TIMESTAMP NULL,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE','LOCKED','CLOSED')),
                           CONSTRAINT chk_failed_count CHECK (failed_login_count >= 0)
);

CREATE TABLE accounts (
                          account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          customer_id BIGINT NOT NULL,
                          account_number VARCHAR(34) NOT NULL UNIQUE,
                          account_type VARCHAR(20) NOT NULL,
                          balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                          CONSTRAINT chk_account_type CHECK (account_type IN ('SAVINGS','CURRENT','DEPOSIT','LOAN')),
                          CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE','FROZEN','MATURED','CLOSED')),
                          CONSTRAINT chk_account_balance CHECK (balance >= 0),
                          INDEX idx_accounts_customer (customer_id)
);

CREATE TABLE deposit_details (
                                 account_id BIGINT PRIMARY KEY,
                                 principal DECIMAL(19,2) NOT NULL,
                                 term_months INT NOT NULL,
                                 interest_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
                                 maturity_date DATE NOT NULL,
                                 CONSTRAINT fk_dep_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
                                 CONSTRAINT chk_dep_term CHECK (term_months > 0 AND principal > 0)
);

CREATE TABLE loan_details (
                              account_id BIGINT PRIMARY KEY,
                              principal_amount DECIMAL(19,2) NOT NULL,
                              annual_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
                              tenure_months INT NOT NULL,
                              start_date DATE NOT NULL,
                              CONSTRAINT fk_loan_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
                              CONSTRAINT chk_loan_vals CHECK (principal_amount > 0 AND tenure_months > 0)
);

CREATE TABLE transactions (
                              transaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              reference_number VARCHAR(40) NOT NULL UNIQUE,
                              source_account_id BIGINT NULL,
                              destination_account_id BIGINT NULL,
                              transaction_type VARCHAR(20) NOT NULL,
                              amount DECIMAL(19,2) NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              description VARCHAR(255),
                              source_balance_after DECIMAL(19,2) NULL,
                              dest_balance_after DECIMAL(19,2) NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_txn_source FOREIGN KEY (source_account_id) REFERENCES accounts(account_id),
                              CONSTRAINT fk_txn_dest FOREIGN KEY (destination_account_id) REFERENCES accounts(account_id),
                              CONSTRAINT chk_txn_amount CHECK (amount > 0),
                              CONSTRAINT chk_txn_type CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER','LOAN_REPAYMENT')),
                              CONSTRAINT chk_txn_status CHECK (status IN ('PENDING','SUCCESS','FAILED','REVERSED')),
                              CONSTRAINT chk_txn_shape CHECK (
                                  (transaction_type = 'DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
                                      OR (transaction_type = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL)
                                      OR (transaction_type IN ('TRANSFER','LOAN_REPAYMENT')
                                      AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL
                                      AND source_account_id <> destination_account_id)
                                  ),
                              INDEX idx_txn_src_time (source_account_id, created_at, transaction_id),
                              INDEX idx_txn_dst_time (destination_account_id, created_at, transaction_id)
);

CREATE TABLE login_audit (
                             audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                             customer_id BIGINT NULL,
                             username_attempted VARCHAR(80) NOT NULL,
                             outcome VARCHAR(20) NOT NULL,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_audit_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                             CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS','FAILURE','LOCKED','TIMEOUT','LOGOUT'))
);

CREATE TABLE transaction_audit (
                                   audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                   customer_id BIGINT NOT NULL,
                                   reference_number VARCHAR(40) NULL,
                                   event_type VARCHAR(30) NOT NULL,
                                   outcome VARCHAR(20) NOT NULL,
                                   failure_code VARCHAR(40) NULL,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   CONSTRAINT fk_tauditcust FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                                   INDEX idx_taudit_customer (customer_id, created_at)
);
```

</details>

`accounts.balance` is outstanding principal for a Loan, not available cash.
Transaction audit outcomes are SUCCESS, REJECTED, FAILED or ROLLED_BACK; failure
codes such as INSUFFICIENT_FUNDS/NOT_OWNER are safe codes, never raw SQL.
The reference transaction status CHECK permits PENDING/FAILED/REVERSED, but the
baseline ledger records successful committed movements only; failed attempts go
to audit. Do not infer reversal or pending-money workflows from the enum alone.

| Business rule                                                | Enforcement                                                                             |
|--------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Unique username/email/account/reference                      | UNIQUE constraints plus friendly prechecks                                              |
| Nonnegative balance; positive amount; allowed statuses/types | CHECK constraints plus model/input validation                                           |
| Legal ledger endpoints                                       | `chk_txn_shape` and transaction factories                                               |
| Referential integrity                                        | FKs                                                                                     |
| Ownership                                                    | Session identity and `WHERE account_id=? AND customer_id=?`; FKs alone do not authorize |
| Type eligibility, maturity, repayment limits                 | Domain policies and services under row locks                                            |

#### Portability note from source section 17.2

The original document compares MySQL with PostgreSQL: AUTO_INCREMENT versus
identity columns; ON UPDATE timestamps versus application/trigger updates;
inline versus separate indexes; InnoDB `FOR UPDATE` versus PostgreSQL row locks;
MySQL deadlock 1213/40001 versus PostgreSQL 40P01; and MySQL CHECK enforcement
beginning at 8.0.16. This is background only. This project uses only the MySQL
driver/schema and does not add an alternative database folder or support claim.

### Detailed workflows

**Source sections 10, 12 and 18.**

#### Registration

1. Validate name, email, phone, username and password.
2. Username: 3–30 characters, letters/digits/`.`/`_`/`-`.
3. Password: minimum 10 characters, mixed classes, not equal to username.
4. Precheck username/email, then rely on UNIQUE constraints for races.
5. Hash, insert ACTIVE customer, map duplicate-key failures safely and clear raw
   password arrays. Do not map every constraint failure to a duplicate message.

#### Authentication and password security

1. Accept credentials without logging them; validate nonblank input.
2. Fetch the customer with a prepared query.
3. Verify a fixed valid dummy hash for unknown users, then return a generic error.
4. Reject CLOSED/LOCKED or future `locked_until` with the same safe response.
5. Verify the adaptive salted hash; never use plain SHA-256 or reversible encryption.
6. Increment failed count; at five failures lock for 15 minutes.
7. Reset failures on success; ensure temporary lock expiry allows recovery.
8. Create session; audit SUCCESS/FAILURE/LOCKED; clear raw password arrays.
9. Password change re-verifies current password, validates and hashes the new
   password, records an audit event and retains the valid session.

The source allows bcrypt, Argon2id or PBKDF2WithHmacSHA256. This project selects
bcrypt cost 12 through PasswordHasher. Document a library-compatible maximum
password byte length so bcrypt never silently truncates input.

#### Shared financial transaction boundary

1. Validate session and amount; create one reference for the logical operation.
2. Acquire one connection, explicitly set READ COMMITTED and disable auto-commit.
3. Lock existing affected rows in ascending account-ID order with `FOR UPDATE`.
4. Re-read and authorize ownership, status, type and funds under the locks.
5. Mutate through domain behavior, persist balances and insert one ledger row.
6. Commit once; on failure roll back the whole unit.
7. Restore connection state and close owned resources without hiding the original failure.
8. Publish success after commit or failure after rollback; write audit separately.
9. Set query timeout (default 10 seconds) and MySQL lock-wait limits.
10. Retry only eligible transient deadlocks/lock timeouts, with short randomized
    backoff and at most three total attempts. MySQL deadlock is 1213/SQLState
    40001; lock-wait timeout handling also needs error 1205 and explicit rollback.
11. Preserve reference across retries and reconcile uncertain commit outcomes
    through `findByReference`; never blindly debit again after connection loss.
12. A post-commit audit failure must not cause committed money movement to run again.

The reference transfer pseudocode creates a reference within its transaction
callback. Preserve a single reference outside the retry loop in implementation
to satisfy the document's idempotency requirement.

#### Deposit, withdrawal and transfer

- **Ordinary deposit:** lock owned ACTIVE Savings/Current; credit amount; insert
  DEPOSIT with null source, destination ID and destination balance-after.
- **Withdrawal:** same ownership/eligibility; verify sufficient funds after lock;
  debit; insert WITHDRAWAL with source ID, null destination and source snapshot.
- **Transfer:** resolve destination number to ID; lock both rows in ID order;
  verify source ownership, both ACTIVE Savings/Current, distinct endpoints and
  funds; debit/credit and insert TRANSFER with both snapshots in one transaction.
  Destination ownership is not required for an ordinary transfer, but destination
  private balance/history/profile must never be disclosed.

#### Deposit opening, maturity and settlement

- Open with principal >= configured minimum, positive term and owned eligible
  funding account. In one transaction debit funding, insert Deposit and its
  details, and insert TRANSFER. No unbacked principal.
- Derive and fix maturity date at creation. On login/listing, lazily mark ACTIVE
  deposits MATURED when maturity_date <= today; no scheduler is required.
- Settle only a MATURED Deposit into an owned ACTIVE Savings/Current account.
  Move principal, zero balance, close and insert TRANSFER atomically.
- Recheck maturity/status under lock and prevent a second settlement.
- No early-release penalties or interest calculation are part of baseline.

#### Loan creation and repayment

- Create positive principal and tenure with start date; persist Loan/details.
  No disbursement, spendable credit or fictional cash ledger row.
- Lock owned funding/Loan rows in ascending ID order.
- Require ACTIVE eligible funding, ACTIVE Loan, funds >= repayment and
  repayment <= outstanding principal.
- Debit funding and reduce Loan with one LOAN_REPAYMENT row; close at zero.

#### History query and pagination

Authorize the selected account before querying. Use parameterized optional date
and type filters, limit 1–50 and page >= 0; choose one documented validation
policy for invalid sizes because the source mentions both rejection and clamping.
Use a stable order and never accept an arbitrary sort column from user input.

```sql
SELECT t.transaction_id, t.reference_number, t.transaction_type,
       t.amount, t.status, t.created_at,
       CASE WHEN t.destination_account_id = ? THEN 'CREDIT' ELSE 'DEBIT' END AS direction
FROM transactions t
WHERE (t.source_account_id = ? OR t.destination_account_id = ?)
  AND t.created_at >= ? AND t.created_at < ?
  AND (? IS NULL OR t.transaction_type = ?)
ORDER BY t.created_at DESC, t.transaction_id DESC
LIMIT ? OFFSET ?;
```

The date predicates are optional filters: omit them safely when no date range is
selected. The range is start-inclusive/end-exclusive. An OR query or correctly
deduplicated UNION can include source/destination rows. Test equal timestamps and
page boundaries; document offset-pagination behavior when new data arrives.

### Lifecycle, validation, errors and observability

**Source sections 19–24.**

| Account state | Behavior                                                                                                               |
|---------------|------------------------------------------------------------------------------------------------------------------------|
| CREATED       | In-memory only; valid persisted account becomes ACTIVE                                                                 |
| ACTIVE        | Operations depend on account type, ownership and funds                                                                 |
| FROZEN        | No customer financial actions; unfreeze only via explicitly authorized trainer/admin script, not a customer admin menu |
| MATURED       | Deposit only; settlement/closure permitted                                                                             |
| CLOSED        | Terminal; no new transactions; closure requires zero balance/debt                                                      |

Customer lifecycle is ACTIVE ↔ LOCKED → CLOSED with temporary-lock rules.
Keep timed lockout and permanent closure distinct.

#### Sessions

SessionContext holds customerId, createdAt and lastActivity from injected Clock.
Before each protected action, `requireActive` checks expiry before refreshing
activity. Default idle timeout is 300 seconds. An expired session is invalidated,
TIMEOUT audited and control returns to welcome. Logout clears the session and
audits LOGOUT. No protected identity is accepted from a customer-ID prompt.

`Scanner.nextLine()` blocks, so recheck expiry every time input returns before
executing the action. An optional daemon watchdog may announce expiry; if added,
use guarded shared state (`volatile`/atomic) and a coordinated nonblocking input
strategy. It is not required for baseline and must not create competing readers.

#### Validation matrix

| Input/action | Required checks                                                                                               |
|--------------|---------------------------------------------------------------------------------------------------------------|
| Registration | Required fields, email, username, password, uniqueness; no partial customer                                   |
| Login        | Required credentials, active/unlocked customer, hash verification; generic failure                            |
| Amount       | BigDecimal parse; > 0; scale <= 2; configured maximum                                                         |
| Account      | Exists; session ownership where required; eligible status/type                                                |
| Withdrawal   | Sufficient balance after locking and re-read                                                                  |
| Transfer     | Distinct endpoints, eligible accounts, owned source; atomic failure                                           |
| Pagination   | Page >= 0; size 1–50; stable ordering; reject unsafe extremes                                                 |
| CLI          | Empty/nonnumeric input, EOF, repeated invalid input; after three failed prompt attempts return safely to menu |

#### Exceptions and user-safe messages

All listed exceptions extend the RuntimeException-based BankingException.

| Exception                   | User response                                             | Logging/audit                                    |
|-----------------------------|-----------------------------------------------------------|--------------------------------------------------|
| ValidationException         | Specific safe field hint                                  | WARN without secrets; audit optional             |
| AuthenticationException     | “Invalid username or password.”                           | INFO outcome; FAILURE/LOCKED audit               |
| AuthorizationException      | “Account not found.”                                      | WARN with internal IDs; REJECTED financial audit |
| SessionExpiredException     | Return to welcome and require login                       | TIMEOUT audit                                    |
| AccountNotEligibleException | Safe inactive/wrong-type explanation                      | Safe outcome/context                             |
| InsufficientFundsException  | “Insufficient funds.”                                     | INFO; REJECTED audit                             |
| PersistenceException        | “The operation could not be completed. Please try again.” | Restricted ERROR/cause; FAILED/ROLLED_BACK audit |

Do not print SQL or stack traces to users. Restricted technical exception logs
must still avoid credentials, hash/password values and full account numbers.
For uncertain commits, use an outcome-aware response rather than encouraging a
blind retry.

#### Configuration reference

| Setting                                         | Source                                                 | Default                                  |
|-------------------------------------------------|--------------------------------------------------------|------------------------------------------|
| DB_URL, DB_USERNAME, DB_PASSWORD                | Private environment                                    | Required once persistence is implemented |
| TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD | Private test environment; project isolation convention | Dedicated guarded banking_test           |
| session.idle.timeout.seconds                    | app.properties                                         | 300                                      |
| db.query.timeout.seconds                        | app.properties                                         | 10                                       |
| tx.max.amount                                   | app.properties                                         | 1000000.00                               |
| auth.max.failed.logins                          | app.properties                                         | 5                                        |
| auth.lock.minutes                               | app.properties                                         | 15                                       |
| password.hash.algorithm                         | app.properties                                         | bcrypt                                   |
| password.hash.cost                              | app.properties                                         | 12                                       |
| history.max.page.size                           | app.properties                                         | 50                                       |

#### Audit and logging

| Event                                        | Destination                   | Fields                                                                 |
|----------------------------------------------|-------------------------------|------------------------------------------------------------------------|
| Login success/failure/locked/timeout/logout  | login_audit                   | Known customer ID or null, attempted username, outcome, timestamp      |
| Financial success/rejection/failure/rollback | transaction_audit             | Customer, reference, event type, outcome, safe failure code, timestamp |
| Technical diagnostics                        | SLF4J/Logback application log | Level, class, reference, safe exception details                        |

Audit writers append only; no update/delete API. Mask account numbers to last
four characters in logs. Never log credentials, raw passwords or hashes.
Failed money attempts audit after rollback in a separate short transaction so
their evidence survives. Audit is not a substitute for the committed ledger.

### Domain-driven design

**Source sections 28–35.** Use lightweight strategic/tactical DDD, not a
distributed architecture. Core subdomain: account rules and ledger/money movement.
Supporting: identity/access. Generic: audit/logging.

#### Shared language

| Term                  | Meaning                                                                                   |
|-----------------------|-------------------------------------------------------------------------------------------|
| Customer              | Registered person who authenticates and owns accounts                                     |
| Credentials           | Username/password input; only encoded hash is stored                                      |
| Session               | Authenticated activity ending in logout or idle expiry                                    |
| Account/type          | Owned Savings, Current, Deposit or Loan record                                            |
| Balance               | Cash for Savings/Current/Deposit; debt for Loan                                           |
| Money                 | Immutable decimal amount with two places; operation amount positive                       |
| Deposit operation     | Adding ordinary cash, distinct from a term Deposit account                                |
| Withdrawal            | Removing eligible available funds                                                         |
| Transfer              | Atomic movement between endpoints; ordinary transfers differ from term funding/settlement |
| Repayment             | Reducing Loan debt from owned eligible funds                                              |
| Ledger entry          | Immutable permanent record of a committed money movement                                  |
| Reference             | Unique system-generated transaction identifier                                            |
| Frozen/Matured/Closed | Lifecycle states with explicit operation restrictions                                     |
| Audit                 | Append-only record of an attempt or outcome                                               |

#### Bounded contexts

| Context               | Owns / exposes                                                       | Relationship                                                         |
|-----------------------|----------------------------------------------------------------------|----------------------------------------------------------------------|
| Identity & Access     | Customer, credentials, session, lockout; customerId and login events | Upstream Customer–Supplier for Account Management                    |
| Account Management    | Account aggregate, lifecycle and eligibility; IDs/numbers/queries    | Shares small AccountId/Money kernel with Ledger                      |
| Ledger & Transactions | Immutable ledger, transfer coordination, history; movement events    | Coordinates locked account mutations within one database transaction |
| Audit & Observability | Append-only login/financial records and logs                         | Conformist event subscriber; no banking decisions                    |

Map these to existing package subareas, not separate services. Cross-context
references use IDs and service/repository boundaries, not uncontrolled table access.

#### Aggregates and value objects

- Customer is an aggregate root identified by customerId; enforce uniqueness
  through database constraints and lifecycle/lockout through domain policy.
- Account is an aggregate root identified by accountId/AccountNumber; one owner,
  nonnegative balance, explicit type/status behavior.
- BankTransaction is immutable, identified by transactionId/reference; positive
  amount, legal endpoints and unique reference.
- LoginAudit/TransactionAudit are append-only entities identified by auditId.
- Money, AccountNumber, TransactionReference, Username, Email, PasswordHash,
  DepositTerms and LoanTerms are value types with value equality/validation.
  Account numbers use a documented generated format/checksum; transaction
  references are unique/non-guessable (time plus randomness).
- Terms require positive principal/term and derived maturity where relevant.
  PasswordHash must never expose its value through `toString()`/logs.
- Enums form closed sets mirrored by database constraints.

Aggregates reference each other by IDs. Account debit/credit behavior enforces
rules and returns valid state; DAO only persists. Financial mutations occur
inside the service's locked transaction. Coordinating two Account aggregates
in one database transaction is an intentional DDD exception to preserve the
brief's atomicity—no eventual consistency, sagas or messaging infrastructure.

#### Policies and repositories

TransferPolicy enforces owner/status/type/distinctness/limits.
AccountEligibilityPolicy implements the operation matrix, maturity and repayment.
PasswordPolicy enforces length/classes/username rule; LockoutPolicy the threshold
and duration. Inject reference/account-number generators for deterministic tests.
Application services authorize, begin transaction, load, call domain behavior,
persist and publish; keep pure banking rules out of orchestration code.

DAO interfaces returning aggregates serve as repositories. JDBC implementations
map rows. Menu requests/DTOs and isolated ResultSet mapping prevent console/SQL
representations from leaking into the domain. EventPublisher and its in-process
implementation belong in service/util.

#### Domain events

| Events                                        | Timing / consumer                                           |
|-----------------------------------------------|-------------------------------------------------------------|
| CustomerRegistered                            | After registration commit; audit optional                   |
| LoginSucceeded, LoginFailed, AccountLockedOut | Auth outcome; login audit                                   |
| SessionExpired, LoggedOut                     | Session invalidation; login audit                           |
| AccountOpened                                 | After account commit; audit optional                        |
| MoneyDeposited, MoneyWithdrawn                | After successful commit; financial audit                    |
| TransferCompleted                             | After commit; financial audit                               |
| TransferRejected, TransferFailed              | After rejection/rollback; independent financial audit       |
| LoanRepaid, LoanClosed                        | After repayment commit / debt reaches zero; financial audit |

Events are small immutable past-tense objects, synchronously published in-process.
Do not publish success before commit, audit failure within the rolled-back unit,
or double-write an outcome through both direct and event-based audit paths.

#### Business invariants

| ID     | Invariant                                                                                                                       | Verification                            |
|--------|---------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| INV-01 | No plaintext/reversible password stored                                                                                         | T01                                     |
| INV-02 | No unauthorized read/change of another customer's accounts; normal transfer credit is allowed without exposing destination data | T06                                     |
| INV-03 | Operation amount > 0, scale <= 2                                                                                                | T13                                     |
| INV-04 | Withdrawal/transfer never creates a negative balance                                                                            | T08, T14                                |
| INV-05 | Transfer all-or-nothing                                                                                                         | T09, T10                                |
| INV-06 | SUCCESS ledger only for committed movement                                                                                      | T10                                     |
| INV-07 | Loan not spendable; repayment never increases/exceeds principal                                                                 | T17                                     |
| INV-08 | Deposit principal released only at maturity                                                                                     | T16                                     |
| INV-09 | Frozen/closed do not transact; matured allows Deposit settlement only                                                           | T11 and account-policy tests            |
| INV-10 | Expired/logged-out session cannot perform protected operations                                                                  | T12 and logout tests                    |
| INV-11 | Audit append-only and secret-free                                                                                               | Audit/security tests and log inspection |
| INV-12 | Account numbers/references unique                                                                                               | Generator and UNIQUE integration tests  |

### Testing and traceability

**Source sections 25 and 36–37.**

| Level           | Coverage / evidence                                                                                                    |
|-----------------|------------------------------------------------------------------------------------------------------------------------|
| Unit            | Validators, Money, policies, password rules, fake-Clock expiry; JUnit reports                                          |
| Service         | Duplicate registration, ownership, funds, transfer and repayment rules; Mockito/fake DAOs                              |
| Mocked JDBC     | Connection/PreparedStatement/ResultSet binding, mapping, cleanup and failure paths                                     |
| DAO integration | Real CRUD, FK/UNIQUE/CHECK, mappings, rollback; repeatable guarded banking_test setup/teardown                         |
| Transaction     | Success, failures after debit/credit/ledger, concurrent withdrawals/opposite transfers; assert final balances and rows |
| Security        | Hash-only storage, invalid login, ownership, SQL injection, timeout/logout                                             |
| Acceptance      | Clean schema → register → login → accounts → deposit/withdraw/transfer → history → timeout/logout                      |

The document lists container-based testing as optional, not a requirement.
This plan uses a dedicated MySQL test database. Mocks cannot substitute for real
locking, isolation or SQL tests. Use `*Test.java` for Surefire and `*IT.java` for
the existing Failsafe profile: `mvn test`, then `mvn verify -Pintegration`.
The PDF's simpler `mvn test` instruction does not by itself run this project's
separately configured integration profile.

#### Essential tests T01–T18

| ID  | Scenario                                    | Required result                                                |
|-----|---------------------------------------------|----------------------------------------------------------------|
| T01 | Valid registration                          | Customer saved with encoded hash, no plaintext                 |
| T02 | Duplicate username/email                    | Rejected; existing data unchanged                              |
| T03 | Correct login                               | Session and customer menu                                      |
| T04 | Wrong password/unknown username             | Same generic denial; failure audited                           |
| T05 | Five consecutive failures                   | Temporary lock; LOCKED audit                                   |
| T06 | Another customer's account                  | No unauthorized access/data leakage                            |
| T07 | Positive deposit                            | Balance and ledger consistent                                  |
| T08 | Withdrawal above funds                      | No balance or ledger change                                    |
| T09 | Eligible transfer                           | Equal debit/credit; exactly one SUCCESS row                    |
| T10 | Forced failure after debit                  | Both balances restored; no SUCCESS row; failure audit survives |
| T11 | Self/frozen/closed transfer                 | Rejected without mutation                                      |
| T12 | Idle past timeout                           | Session invalid; next protected action requires login          |
| T13 | Malformed/negative/three-decimal amount     | Safe rejection; no database mutation                           |
| T14 | Parallel withdrawals and opposite transfers | No overspend; bounded deadlock handling                        |
| T15 | Pagination boundary                         | Stable ordering; no duplicate/omitted rows in fixed dataset    |
| T16 | Early/mature Deposit settlement             | Early refused; mature succeeds                                 |
| T17 | Partial/exact/excess Loan repayment         | Debt reduced; exact closes; overpayment refused                |
| T18 | SQL-injection text inputs                   | Bound as data or safely validated; no SQL structural effect    |

Also test lock expiry, successful-login reset, password/profile changes, logout,
query/lock timeouts, resource cleanup, type/status matrix, audit failure after
commit and uncertain-commit reconciliation.

#### Evidence to retain for evaluation

- Executed JUnit/Surefire and Failsafe reports, with failures/skips disclosed.
- Before/after balances and ledger counts for a successful transfer and an
  intentionally failed transfer in isolated tests.
- Independent-connection concurrency results demonstrating no overspending.
- Synthetic stored-password inspection showing an encoded hash; do not publish
  the hash or raw password in screenshots/logs.
- Owner-scoped access-denial, timeout/logout and lockout-expiry test outcomes.
- Audit-table checks proving that rolled-back failures remain recorded and
  successful money movements are not duplicated when audit logging fails.
- A fresh-database setup and demonstration that another developer can reproduce.

Keep generated reports under ignored build output. Do not describe expected
results or unchecked checklists as actual test evidence.

#### Requirement-to-design mapping

| Requirements | Design                                                                 | Evidence                                       |
|--------------|------------------------------------------------------------------------|------------------------------------------------|
| F01–F02      | AuthService, CustomerDAO, PasswordHasher, LoginAuditDAO, LockoutPolicy | T01–T05, stored-hash inspection                |
| F03          | CustomerService, AuthService, CustomerDAO                              | Profile/password unit/service tests            |
| F04–F05      | Account hierarchy, AccountService, details tables                      | T06/T16/T17, multiple-account creation/listing |
| F06–F07      | TransactionService, AccountDAO, TransactionDAO                         | T07–T08                                        |
| F08          | Shared transaction, locks, retry                                       | T09–T11/T14                                    |
| F09–F10      | AccountService, TransactionDAO history                                 | Owner-scoped balance tests, T15                |
| F11          | SessionService, SessionManager, Clock                                  | T12, logout                                    |
| F12          | Constraints, prepared SQL, JdbcTransaction                             | T13/T18, DAO tests                             |
| F13          | Audit DAOs, events, safe logs                                          | Audit-table and redaction tests                |
| F14          | Complete layered test suite                                            | Executed reports and coverage review           |

### Milestones, evaluation and submission

**Source sections 26–27 and 37–41.** For the detailed work order see
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). Setup requires JDK 17,
Maven 3.9+, MySQL 8.0.16+, application/test databases, least-privilege credentials,
the implemented schema, private environment configuration and all required
Maven dependencies. Then test, package and launch with the commands above.
No database prerequisite is needed for the current scaffold-only entry point.

| Milestone | Work                                                                   | Exit criteria                                                |
|-----------|------------------------------------------------------------------------|--------------------------------------------------------------|
| P1-M1     | Requirements, use cases/class/ER diagrams, repository, schema skeleton | Design/repository ready; database connects                   |
| P1-M2     | Registration, hashing, login, connections                              | Positive/negative login tests; Week 2 review                 |
| P1-M3     | Profile, accounts/listing; all four types represented                  | Ownership/type/status validation                             |
| P1-M4     | Deposit, withdrawal, balance                                           | BigDecimal and invalid/funds checks; Week 3 interim          |
| P1-M5     | Atomic transfer, rollback, history                                     | Rollback/concurrency tests pass                              |
| P1-M6     | Session, validation, exceptions, audit, Deposit/Loan                   | No secrets logged; timeout/logout and specialized flows work |
| P1-M7     | Test completion, README, demo                                          | Reproducible clean setup; Week 3 final evaluation            |

#### Definition of done and mandatory gates

Build from a clean checkout with reproducible schema instructions; all four types
work under explicit rules; passwords are hashes; ownership is checked for every
protected operation; transfers remain atomic under failure/concurrency; history,
timeout/logout and automated success/failure tests work.

Regardless of total marks:

- No plaintext password storage.
- No unauthorized cross-customer account access.
- No successful withdrawal exceeding available funds.
- No partial transfer when any step fails.
- No concatenation of untrusted input into SQL.
- Successful demonstration from a clean database using documented instructions.

#### Evaluation rubric

| Area                                | Marks   | Assessment                                                     |
|-------------------------------------|---------|----------------------------------------------------------------|
| Core Java, OOP and code quality     | 25      | Classes, interfaces, collections, enums, maintainability       |
| JDBC, SQL and schema                | 20      | DAO separation, constraints, prepared statements, indexing     |
| Banking and transaction integrity   | 25      | Operations, transfers, rollback, ledger and account-type rules |
| Exceptions, validation and security | 15      | Hashing, authorization, timeout, safe input/errors             |
| Testing, Git and documentation      | 15      | Meaningful coverage/commits, README, reproducibility           |
| **Total**                           | **100** | Mandatory gates still apply                                    |


#### Final submission checklist

- [ ] Java source in layered packages.
- [ ] MySQL schema and optional synthetic sample data without reusable passwords.
- [ ] Maven POM and dependency notes.
- [ ] Registration and secure login.
- [ ] Savings, Current, Deposit and Loan support.
- [ ] Deposit, withdrawal, transfer and repayment workflows.
- [ ] Transaction history and append-only audit.
- [ ] Session timeout and logout.
- [ ] JUnit/Mockito tests, including real transfer rollback and concurrency.
- [ ] README setup, configuration, commands and AI-assistance log.
- [ ] Git repository with meaningful commits.
- [ ] Successful and failed demo scenarios.

These are final-evaluation items, not a current completion report.

#### Future work, not baseline scope

Configurable overdraft; Deposit interest/early-closure penalties; Loan schedules
and disbursement; richer history search; statement export; optimistic versioning;
audit retention; migration tooling such as Flyway/Liquibase; MFA concepts; and a
later service/API layer. Do not implement these at the expense of P1's Core Java,
JDBC, SQL and transaction-integrity objectives.

#### Decisions that the document does not fully specify

The PDF defines required behavior but deliberately leaves implementation details
to the participant. Record the following choices in
[docs/decisions.md](docs/decisions.md), rather than presenting invented values as
requirements from the document:

| Detail                     | Required clarification before its feature is completed                                               |
|----------------------------|------------------------------------------------------------------------------------------------------|
| Username/email uniqueness  | Normalization, case sensitivity and matching MySQL collation behavior                                |
| Password character classes | Precisely which mixed classes are required; bcrypt-compatible UTF-8 maximum                          |
| Account numbers            | Synthetic format, checksum algorithm, generation/collision handling; do not claim real IBAN validity |
| Deposit terms              | Minimum principal, allowed term range and date/time-zone policy                                      |
| Loan terms                 | Allowed principal/tenure range and simplified account-creation rules                                 |
| Profile edits              | Explicit permitted fields and whether username/email changes are supported                           |
| Timed lockout              | How status and locked_until interact when the temporary lock expires                                 |
| Password-change audit      | Event/storage mapping; the reference login_audit CHECK has no PASSWORD_CHANGED outcome               |
| Pagination                 | Choose/document rejection versus clamping; define behavior under new history inserts                 |
| Audit unavailability       | What is reported/reconciled after a primary commit if separate audit persistence fails               |
| Commit uncertainty         | Preserve references; reconcile before any retry that could duplicate a movement                      |

The plan recommends three **total** transaction attempts, invalid-page-size
rejection, session checks before activity refresh, and a single reference across
retries. These are explicit implementation interpretations of the source's
integrity requirements, not additional banking features.

#### Source coverage index

| Document sections           | README coverage                                                                   |
|-----------------------------|-----------------------------------------------------------------------------------|
| Control/conventions, 1–5, 9 | Document control; purpose, scope, roles, requirements and account rules           |
| 6–8                         | Architecture, decisions, component/deployment and diagram guide                   |
| 10–12                       | Authentication, database and shared transaction boundary                          |
| 13–16                       | Structure, class inventory, packages, service/DAO contracts                       |
| 17–18                       | Complete reference DDL, constraints, portability and workflows                    |
| 19–24                       | Lifecycle, sessions, validation, exceptions, config and audit/logging             |
| 25–27                       | Test strategy/cases, build guidance and milestones                                |
| 28–35                       | DDD language, contexts, aggregates, policies, events, repositories and invariants |
| 36–38                       | Traceability, acceptance gates and rubric                                         |
| 39–41                       | AI/governance, submission and future scope                                        |

## Next steps

Follow [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)