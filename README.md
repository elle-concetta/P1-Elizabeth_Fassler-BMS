# P1-Elizabeth_Fassler-BMS

P1-Elizabeth_Fassler-BMS is a command-line banking application being built with
**Java 27, Maven, JDBC, and MySQL 8.4.x**. It is a learning project based on the
Secure Banking CLI design document, version 2.0 (26 October 2026).

The goal is to let customers register, sign in, manage accounts, and carry out
banking transactions while learning how to handle validation, security, and
database transactions correctly.

## Current status

For the training demo, see [the completed and remaining work checklist](docs/current-progress.md).
It separates implemented code, confirmed local tests and unverified live behavior.

**Immediate local focus:** resume guided implementation under the full project
guidelines rather than the earlier overnight deadline. First confirm local
startup after setting the missing terminal database variables; do not assume
that the welcome menu proves database connectivity.
The user reports finishing the source-batch transfer.
After adding the missing LoginAuditDelivery helper, the user supplied a
successful local Java 27 run: all 39 batch tests passed, with no failures,
errors, or skips. Do not repeat that unchanged check.
The user supplied both existing customers and login_audit definitions. Their
columns, constraints and types match the current registration/login SQL;
no schema change is indicated. After aligning the audit-writer test's mock
connection method, local `mvn package` passed all 295 tests with no failures,
errors or skips. It produced
`target/P1-Elizabeth_Fassler-BMS-1.0-SNAPSHOT.jar`. No unchanged rerun is needed.
Next launch the CLI from PowerShell using the runtime dependency classpath,
confirm the welcome menu, and choose Exit before real registration/login.
Live authentication is unverified.
The source-only ZIP is `generated-artifacts/cli-batch-1-source.zip`; it contains
23 Java files and the guide, including the matching audit-writer regression
test, not SQL, settings or credentials.

The combined demo run already passed all 64 tests locally: 32 financial writer,
20 audit model, and 12 login connection cases, with no failures, errors, or
skips. Demo preparation can resume later without repeating that unchanged run.

The first guided feature batch now connects terminal registration, login,
profile display, lockout, session expiry and logout. The workspace build passed
315 database-free tests, including 39 new batch cases. This is not a finished
banking app: account creation, money operations and other required workflows
remain. The user's copied batch passed its 39 selected unit tests; live
authentication writes remain unverified. The supplied table definitions are
compatible with the current SQL; permissions and runtime connectivity are
separate checks.
See [the batch-one copy and test guide](docs/cli-batch-1-guide.md).

### SQL-mode timestamp verification

The isolated login-audit tests now support read-only expectations for default
strict, permissive, and `TIME_TRUNCATE_FRACTIONAL` server modes. The new
`src/test/java/com/example/banking/integration/LoginAuditSqlModeProfileTest.java`
checks those expectations without a database. All 276 database-free tests
passed on Java 27. The user approved the isolated servers: the strict run
passed, the permissive run failed, and both servers were shut down. The third
run remains pending; verification is paused for the guided local walkthrough.
See [the SQL-mode verification procedure](docs/login-audit-verification.md)
before any live test writes. This does not change application SQL modes or
establish local IntelliJ verification.

### Where to pick up now

**Latest local output:** after adding the missing
`ConnectionFactory.getLoginAuditConnection()` method, the selected financial
audit tests passed on Java 27: 32 writer tests and 20 model tests, with no
failures, errors, or skips. The compilation blocker is resolved.
The first database-free UTC setup test in
`src/test/java/com/example/banking/unit/LoginAuditConnectionTest.java`,
`establishesUtcAndClosesSetupBeforeReturningConnection`, also passed locally:
one test, no failures, errors, or skips. The failure-cleanup method
`rejectedSessionSetupClosesStatementAndConnectionWithoutTransaction` also
passed in the user's next selected one-test run.

**Next local action:** build the runtime classpath with Maven's dependency
plugin and launch BankingApplication directly in PowerShell. Confirm the
welcome menu and choose Exit before any real registration/login.
The runtime classpath was generated successfully. The first launch failed
because all three DB_* variables were absent from that terminal; a safe
presence-only check confirmed this. Set the existing values privately in the
same session and relaunch—no unchanged build or schema action is needed.
Keep the local project configuration and database.
Do not run schema.sql or create/drop tables. The optional SQL-mode
investigation remains paused; do not repeat the completed individual tests.

**Where logging goes:** `src/main/resources/logback.xml` currently configures
SLF4J/Logback console diagnostics to stderr at INFO level. The helper is
`src/main/java/com/example/banking/util/LoggerFactory.java`. The application
currently logs its startup; registration/login diagnostic events and a rolling
log file are not implemented. `demo-test-results.log` is a saved Maven
transcript, not an application log. Database audit rows are separate and are
not inserted by mocked unit tests. The batch archive intentionally preserves
the existing resources and logging helper rather than replacing them.

**New financial-audit increment:** the writer and its Mockito test file are now
implemented, not placeholders:

```text
src/main/java/com/example/banking/dao/TransactionAuditDAO.java
src/main/java/com/example/banking/dao/jdbc/JdbcTransactionAuditDAO.java
src/test/java/com/example/banking/unit/JdbcTransactionAuditDAOTest.java
```

Copy those three files to the same locations in IntelliJ. Keep the existing
TransactionAudit, TransactionReference, TransactionType, AuditOutcome,
ConnectionFactory, and PersistenceException classes, then run on Java 27:

```powershell
mvn "-Dtest=JdbcTransactionAuditDAOTest,TransactionAuditTest" test
```

**Local checkpoint passed (11 November 2026):** all 52 tests passed on Java 27:
32 JDBC test cases and 20 existing model tests, with zero failures, errors, or
skips. The missing-helper compilation blocker is resolved. The earlier Java 21
compatibility check also passed, but this local Java 27 result is the relevant
project checkpoint. No unchanged rerun is needed. This command uses mocks,
not a MySQL connection; real persistence remains a separate verification step.

**If Maven reports a missing LoginAuditTestDatabase:** copy the existing
`src/test/java/com/example/banking/integration/LoginAuditTestDatabase.java`
helper as well. Its package must be `com.example.banking.integration`; keep it
under test sources, not main sources. LoginAuditDatabaseGuardTest imports this
helper. Maven compiles every test source before applying `-Dtest`, so a missing
helper blocks even the financial-audit test command. Copying or compiling this
helper does not create fixtures or open a database connection. The integration
profile is not needed for the unit-test command.

If the local HashMap type-inference error remains after restoring that helper,
compare LoginAuditDatabaseGuardTest with this repository's current version.
Do not replace its map with a capacity integer or remove the safety checks.

IntelliJ's **Unable to resolve table 'transaction_audit'** inspection inside
the test's SQL string is separate from Java compilation. The mock tests do not
execute that SQL. For editor validation, refresh the existing MySQL data source
and include banking_db in its selected schemas. Do not recreate tables just to
remove an editor warning.

The writer accepts only unsaved records, uses a fresh connection, sets its
own session to UTC, and commits the audit independently. Future services must
call it **after** the primary money transaction commits or rolls back. It
does not move money, create a ledger row, or retry an uncertain save.

The existing whole-second TIMESTAMP column cannot preserve fractional instants.
This writer rejects fractional or out-of-range times before acquiring JDBC;
it never silently rounds the evidence. Accepted instants run inclusively from
1970-01-01T00:00:01Z to 2038-01-19T03:14:07Z. Future service wiring must retain
rejected evidence explicitly rather than discard it. No schema changes are
needed for this increment. Real MySQL durability remains a separate check.

**Local checkpoint passed (11 November 2026):** the combined login-audit command
below completed on Java 27 with 59 tests, zero failures, zero errors, and zero
skips: LoginAuditTest (20), LoginAuditCaptureTest (6), and JdbcLoginAuditDAOTest
(33). Both focused audit checkpoints are now confirmed locally. Next, prepare
isolated MySQL integration testing without modifying banking_db.

The complete 20-test `LoginAuditTest.java` is saved under
`src/test/java/com/example/banking/unit/`. If your local copy is blank, replace
it with this project's current file, then run
`mvn "-Dtest=LoginAuditTest" test` before the combined audit check below.

Keep the existing Java 27 configuration and project files; no application file
has been identified as missing or needing restoration. LoginAudit, its JDBC
writer, and the oversized-attempt capture helper are now implemented in this
repository. Older walkthroughs describing LoginAudit as a placeholder are
historical, not the next task.

Copy the current login-audit classes and matching unit tests into the local
IntelliJ project, reload Maven, and run:

```powershell
mvn "-Dtest=LoginAuditTest,LoginAuditCaptureTest,JdbcLoginAuditDAOTest" test
```

This checks the model, capture policy, and mocked JDBC interactions without
connecting to MySQL. Do not rerun schema.sql against the existing banking_db.
Real persistence checks come afterward, on an isolated test database.
The earlier 105-test result does not cover these new audit changes.

The project is still in development. The folder structure, starter Java classes,
Maven configuration, resources, and SQL schema are in place. Registration, login,
account operations, and the interactive menus still need to be built.

The entry point in this repository currently prints a short notice and exits;
it also emits a safe startup diagnostic to `System.err`. It does not open a
database connection.

### Local implementation progress

Development is taking place in a local IntelliJ project, which is further along
than the starter Java files in this repository. Here is the progress confirmed
so far:

- `ConnectionMockTest` passed locally with Java 27 and Mockito. The shared
  one-test version is now included in this repository too.
- The local database tables and triggers are finished.
- The latest shared combined unit run passed all 38 tests, with no failures,
  errors, or skipped tests: 28 configuration tests, nine factory tests, and
  one Mockito setup test. The complete configuration tests include both
  startup validation and environment-variable reading.
- This repository now includes `AppConfig.java` and `AppConfigTest.java` with
  the configuration code and all 28 tests from the walkthrough, including
  startup validation and environment-variable reading. These tests have not
  been executed here; the focused identifier run did not include them.
  The complete suite has passed in the local project; it has not been run here.
- `ConnectionFactory.java` and its nine Mockito tests are now included here.
  The factory reads private settings and opens connections without running
  SQL or managing transactions. The shared local Maven output confirms all
  nine tests passed on Java 27, with no failures, errors, or skipped tests.
  They were not included in the focused identifier run here.
  The subsequent local read-only JUnit run through this factory finished
  with exit code 0 and no reported errors.
- Workbench successfully executed `SELECT 1 AS connection_check`, returning
  one row. This confirms Workbench connectivity, not the Java factory.
- `ConnectionFactoryReadOnlyIT.java` completed locally in IntelliJ with exit
  code 0 and no reported errors. It checks the real factory connection,
  selected `banking_db` database, and the result of `SELECT 1` without writes.
  It has not been run in this workspace.
- `MySqlConnectionTest` passed locally after its private environment variables
  were configured. It checks a real JDBC connection and runs `SELECT 1`.
- Logging now uses SLF4J with the Logback console appender at INFO level.
  The shared logger helper masks account numbers, and `LoggingTest.java`
  includes six tests. The shared local Maven result confirms all six passed,
  with no failures, errors, or skipped tests. They have not been run here.
  The separate local startup run also displayed the expected INFO diagnostic
  and scaffold messages, then exited with code 0. Step 4 is complete for this
  guided increment; banking workflows remain unimplemented.
- Step 5 has started with `Money.java` and 19 tests in `MoneyTest.java`.
  Your shared result for the Money/account-number/reference walkthrough shows
  all 35 tests passed, with no failures, errors, or skipped tests.
  The project target remains Java 27; the summary itself does not show the
  Maven runtime version. The enum increment adds ten tests in
  `EnumContractTest.java`. All 45 focused tests passed here on Java 21 with
  a command-line-only compiler override, not a POM change. You have now confirmed
  that the enum tests pass locally on Java 27. `TransactionReference.java` and
  its 12 tests passed locally on Java 27, as you have now confirmed.
  `PasswordHash.java` and 12 deterministic tests are included.
  All 69 focused tests passed here on Java 21 with a command-only compiler
  override; you have now confirmed PasswordHash tests pass locally on Java 27.
  `AuditOutcome.java` and eight tests were the preceding increment. All 65 focused
  tests passed here on Java 21 before syncing PasswordHash; the expanded combined
  run then passed all 77 tests. You have now confirmed the AuditOutcome tests
  pass locally on Java 27.
  `TransactionAudit.java` now implements the approved immutable attempt-record
  contract, with 20 tests. All 97 focused tests passed in the workspace on
  Java 21 using a command-only release override; the later combined 105-test
  local Java 27 result includes TransactionAudit.
  `LoginOutcome.java` and eight tests are the separate login increment.
  All 85 focused tests (excluding TransactionAuditTest) passed here on Java 21 with a
  command-only compiler override. You have now confirmed LoginOutcomeTest
  passes locally on Java 27.
  `LoginAudit.java` and 20 construction tests are included. Main-project updates
  record those 20 tests passing locally on Java 27; the combined 125-test
  local run remains pending.
  Other step 5 value types remain separate increments.
- Existing databases were reported present after the MySQL upgrade steps.
  Confirm `SELECT VERSION();` reports `8.4.x` before using Connector/J 26.x.
- The last shared `customers` definition included separate name fields and
  enum status. The table was empty at the time of that check.
- `sql/mysql/schema.sql` defines the seven baseline tables plus daily balances
  and separate ledger-change auditing: nine tables total. It includes the DOB
  and ledger-change triggers. Execution against MySQL has not been verified here.

The local progress above comes from shared test output and setup confirmations.
Those checks have not been rerun against this repository. Updating these
documents does not copy code into IntelliJ or change the local database.

### Keeping the documentation current

These documents will grow with the application. The README explains how the
project works and how to run it, the implementation plan tracks the next steps,
and `docs/decisions.md` explains the choices made along the way. Completed work
and pending checks should be easy to tell apart.

This is an educational simulation, not a real bank. It runs in the terminal and
uses JDBC directly, without Spring Boot, an ORM, or a web interface. It does not
connect to payment networks or move real money.

## Contents

- [Structure](#structure)
- [Build and run](#build-and-run)
- [Required libraries](#required-libraries)
- [Full application setup checklist](#full-application-setup-checklist--after-implementation)
- [Step 1 walkthrough](#step-1--maven-and-first-test-walkthrough)
- [Local MySQL connection check](#local-mysql-connection-check)
- [Schema implementation walkthrough](#schema-implementation-walkthrough)
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
- [AI-assistance log](#ai-assistance-log)

## Structure

```text
(project root)
├── .idea/
│   ├── .gitignore
│   ├── compiler.xml
│   ├── encodings.xml
│   ├── jarRepositories.xml
│   ├── misc.xml
│   └── workspace.xml
├── .mvn/                         empty; no Maven wrapper installed
├── .gitignore
├── pom.xml
├── README.md
├── IMPLEMENTATION_PLAN.md
├── docs/decisions.md
├── sql/
│   ├── mysql/schema.sql          extended nine-table MySQL initial schema
│   └── sample-data.sql           optional synthetic data
├── src/main/resources/
│   ├── app.properties
│   └── logback.xml
├── src/main/java/com/example/banking/
│   ├── app/                      entry point, menus, input
│   ├── model/                    domain types and enums
│   │   ├── Money.java            immutable decimal value object
│   │   ├── AccountNumber.java    immutable synthetic number with Luhn validation
│   │   ├── TransactionReference.java immutable, exact-format correlation reference
│   │   ├── PasswordHash.java     immutable bcrypt encoding with redacted rendering
│   │   ├── AuditOutcome.java     closed financial-attempt outcomes
│   │   ├── TransactionAudit.java immutable, validated financial-attempt record
│   │   ├── LoginOutcome.java     separate closed authentication/session outcomes
│   │   ├── LoginAudit.java       immutable minimal login evidence; no persistence
│   │   ├── AccountType.java      persisted account types
│   │   ├── AccountStatus.java    persisted statuses; excludes CREATED
│   │   ├── CustomerStatus.java   persisted customer statuses
│   │   ├── TransactionType.java  persisted movement types
│   │   ├── TransactionStatus.java reference ledger statuses
│   │   └── policy/               eligibility, transfer, password, lockout
│   ├── service/                  use cases and transaction boundaries
│   ├── dao/                      repository interfaces
│   │   └── jdbc/                 JDBC implementation placeholders
│   ├── security/                 hashing and session contracts
│   ├── validation/               input validation placeholders
│   ├── exception/                typed failures
│   └── util/                     configuration, connections, transaction helper
│       ├── AppConfig.java
│       ├── ConnectionFactory.java
│       ├── LoggerFactory.java
│       └── ReferenceGenerator.java secure account/reference candidates
└── src/test/java/com/example/banking/
    ├── unit/
    │   ├── AppConfigTest.java        configuration tests included here
    │   ├── ConnectionFactoryTest.java connection-opening tests included here
    │   ├── ConnectionMockTest.java   JUnit/Mockito setup test included here
    │   ├── LoggingTest.java          six logging tests passed locally
    │   ├── MoneyTest.java            19 money tests passed locally
    │   ├── AccountNumberTest.java    eight account-number tests
    │   ├── ReferenceGeneratorTest.java eight generator tests
    │   ├── TransactionReferenceTest.java twelve reference validation tests
    │   ├── PasswordHashTest.java twelve encoding, equality, and redaction tests
    │   ├── AuditOutcomeTest.java     eight outcome/SQL-text contract tests
    │   ├── TransactionAuditTest.java 20 construction, immutability, and rendering tests
    │   ├── JdbcTransactionAuditDAOTest.java 32 mocked financial-audit writer cases
    │   ├── LoginOutcomeTest.java     eight login outcome/SQL-text contract tests
    │   ├── LoginAuditTest.java       20 construction, nullability, and immutability tests
    │   ├── LoginAuditCaptureTest.java six oversized-attempt capture tests
    │   ├── JdbcLoginAuditDAOTest.java 33 mocked login-audit writer cases
    │   ├── LoginAuditDatabaseGuardTest.java isolated database safety checks
    │   └── EnumContractTest.java     ten persisted-name/schema checks
    ├── service/
    └── integration/
        ├── ConnectionFactoryReadOnlyIT.java read-only factory connectivity check
        └── MySqlConnectionTest.java  local-only JDBC connection check
```

The files listed under `util` cover step 4 and step 5's identifier generator;
other utility starter files are omitted for brevity. All eleven unit test files
shown above are present in this repository.
`MySqlConnectionTest.java` is shown as a local-only file from the walkthrough;
it has not been copied into this repository.

Keep `ConnectionMockTest.java` when adding `ConnectionFactoryTest.java`.
The first checks that Mockito can simulate a JDBC connection; it does not call
the factory or open MySQL. The second tests the application's connection-opening
code using mocked collaborators. They have different purposes.

### Configuration files used in step 4

| File | Location | What it does |
|---|---|---|
| `AppConfig.java` | `src/main/java/com/example/banking/util/AppConfig.java` | Loads configuration and checks settings |
| `AppConfigTest.java` | `src/test/java/com/example/banking/unit/AppConfigTest.java` | Tests required values, integers, monetary precision, bcrypt settings, and the configuration additions as they are completed |
| `ConnectionFactory.java` | `src/main/java/com/example/banking/util/ConnectionFactory.java` | Opens MySQL connections using private settings; the caller closes each connection |
| `ConnectionFactoryTest.java` | `src/test/java/com/example/banking/unit/ConnectionFactoryTest.java` | Uses Mockito to test connection opening and failure handling without MySQL |
| `app.properties` | `src/main/resources/app.properties` | Holds non-secret application settings |
| `LoggerFactory.java` | `src/main/java/com/example/banking/util/LoggerFactory.java` | Supplies SLF4J loggers and explicit account-number masking |
| `logback.xml` | `src/main/resources/logback.xml` | Routes INFO and higher diagnostics to stderr, with a reference field |
| `LoggingTest.java` | `src/test/java/com/example/banking/unit/LoggingTest.java` | Checks the logging provider, configuration, masking, and parameterized messages |

`AppConfigTest.java` uses the package `com.example.banking.unit`. It belongs
under `src/test/java`, not alongside application code or in `resources`.

### Logging walkthrough: finish step 4

The existing Maven dependencies already include SLF4J and Logback; no additional
dependency is needed for this increment.

1. Copy the current `LoggerFactory.java`, `logback.xml`, and
   `BankingApplication.java` into their matching local paths. Copy
   `LoggingTest.java` into the existing `unit` test package.
2. Run `mvn "-Dtest=LoggingTest" test` from the project root. This selects six
   tests and needs no MySQL connection or environment variables. The tests check
   the real Logback provider, class-based logger naming, the stderr/INFO
   configuration, null-source validation, masking, and a captured formatted
   message. They restore the test logger's settings afterward.
3. Run `BankingApplication` from IntelliJ. Expect a timestamped INFO diagnostic
   containing `Banking CLI started` and the existing two scaffold messages.
   Diagnostics use stderr; normal CLI text uses stdout. IntelliJ can display
   these streams interleaved, so their visual order is not significant.
4. Record the actual local test result before treating step 4 as finished.
   Build `Money.java` next, not menus or financial operations.

The log pattern includes `[ref=none]` until a future operation supplies a safe,
generated correlation reference in SLF4J MDC under the key `reference`.
When MDC is used, clear it after the operation, including failure paths.
There is no file appender or persistent audit implementation in this increment.
Database audit tables remain separate from application diagnostics.

Call `LoggerFactory.maskAccountNumber(...)` before including an account number
in a message. Longer alphanumeric values expose only their last four characters;
missing, short, or malformed values are entirely hidden. This is a logging
helper, not account-input validation. It does not automatically redact arbitrary
messages. Never pass credentials, hashes, raw SQL exceptions, customer details,
or objects containing sensitive fields to a logger. Parameterized `{}` messages
avoid string concatenation but do not make sensitive arguments safe.

The Maven files are directly at the project root, with no extra enclosing folder.
This layout follows the supplied IntelliJ screenshot. Open the project or its
`pom.xml` in IntelliJ, select an installed JDK 27, and import the Maven project.
The `.idea` files are portable starter settings; IntelliJ will update local workspace
settings. `External Libraries` and `Scratches and Consoles` are IDE views, not
physical project directories. The empty `.mvn` folder does not provide `mvnw`;
use an installed Maven.

### Money walkthrough: start step 5

1. Replace the placeholder in
   `src/main/java/com/example/banking/model/Money.java` with the included class.
2. Create `src/test/java/com/example/banking/unit/MoneyTest.java` in IntelliJ
   and copy the included test class. Keep all existing tests.
3. Run `mvn "-Dtest=MoneyTest" test`. Expect 19 tests if the copied files match.
   No environment variables, Mockito, or MySQL connection are needed.
4. All 19 tests have now been confirmed passing locally. Continue with the
   identifier walkthrough below; keep the remaining value types separate.

`Money.parse("10")`, `Money.parse("10.1")`, and `Money.parse("10.12")`
produce `10.00`, `10.10`, and `10.12`. Surrounding whitespace and a leading
sign are accepted. Scientific notation, commas, currency symbols, malformed
text, and more than two fractional digits are rejected. `10.120` is rejected
even though rounding could remove the final zero.

`new Money(BigDecimal)` supports JDBC values and uses the same strict scale
and range checks. Use string-based BigDecimal construction, not floating-point
inputs. `add` and `subtract` return new values; equality and hashing use the
normalized two-place amount. `toBigDecimal()` exposes an immutable BigDecimal,
not a mutable balance.

Values and arithmetic results must fit `DECIMAL(19,2)`:
at most 17 integer digits and two fractional digits. The storage boundary is
not the configurable per-operation maximum. Money itself can represent zero
or negative differences; `isPositive()` returns false for both. This does not
permit overdrafts or negative persisted account balances. Account rules will
enforce nonnegative balances, and later transaction validation will reject
nonpositive operations and amounts above the configured maximum.

## Build and run

Use JDK 27 and Maven 3.9+ for the updated local setup. Verify the runtime with
`mvn -version`: a compiler release property does not select Maven's Java runtime.
The checked-in POM now targets Java 27 and includes the Mockito dependencies and
explicit test agent used in the walkthrough. To test and build the JAR, run:

```bash
mvn test
mvn clean package
java -jar target/banking-cli.jar
```

The repository includes JAR packaging, 28 configuration tests, and nine
connection-factory tests. The full commands above have not been verified here
on Java 27. Packaging and launching the local project as an executable JAR
still need to be checked.

For future database integration tests named `*IT.java`:

```bash
mvn verify -Pintegration
```

Implement test database safety guards before writing tests that mutate data.

## Required libraries

| Library | Purpose | Current status |
|---|---|---|
| MySQL Connector/J | JDBC connections and MySQL operations | Declared in `pom.xml` |
| JUnit Jupiter | Automated tests | Local setup tests reported passing |
| Mockito Core | Mock DAOs and JDBC objects for isolated tests | Local mock-connection test reported passing |
| Mockito JUnit Jupiter | Initialize Mockito mocks in JUnit 5 | Used in the local mock-connection test |
| SLF4J API | Logging interface | Declared |
| Logback Classic | Logging implementation, configured by `logback.xml` | INFO/stderr configuration; all six logging tests passed locally |
| bcrypt | Salted adaptive password hashing and verification | Declared; implementation pending |

### Dependency coordinates and versions

The versions below match the local setup guidance and are now declared in the
repository's POM. This does not mean the full dependency tree has been built or
security-checked here. The project
guidelines name the required libraries but do not pin their versions.
Keep checking dependency security warnings when updating the build.

| Maven coordinate | Version | Scope |
|---|---|---|
| `com.mysql:mysql-connector-j` | 26.10.0; MySQL Server 8.4+ | Application |
| `at.favre.lib:bcrypt` | 0.10.2 | Application |
| `org.slf4j:slf4j-api` | 2.0.16 | Application |
| `ch.qos.logback:logback-classic` | 1.6.6; matching core is transitive | Application |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | Test |
| `org.mockito:mockito-core` | 5.24.0 | Test |
| `org.mockito:mockito-junit-jupiter` | 5.24.0 | Test |

The local configuration uses Compiler 3.13.0 with release 27, Surefire 3.5.2,
and Dependency Plugin 3.11.0 to resolve the Mockito agent. Mockito 5.24.0 brings
Byte Buddy 1.17.7 without a separate override.

The checked-in build also includes Shade 3.6.0 for packaging and
Failsafe 3.5.2 for the `integration` profile. Its main class is
`com.example.banking.app.BankingApplication`. Those packaging settings are not
confirmed in the local POM merely because its tests pass.

Choose the driver using the **server version**, not Workbench or Shell versions.
Connector/J 9.7.0 documents support for MySQL 8.0+, while 26.7.0 and 26.10.0
document support for 8.4+. The earlier 8.0.46 setup raised a security warning
recommending an unaffected driver; do not suppress a CVE warning to retain an
older compatible driver. Refresh dependency inspections after upgrades.

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

Use JDK 27 and Maven 3.9+. Confirm that Maven also reports Java 27.
In IntelliJ, open **File → Project Structure → Project** and select JDK 27.
Under **Settings → Build, Execution, Deployment → Build Tools → Maven**, select
an installed Maven or IntelliJ's bundled Maven.

If the terminal cannot find `mvn`, the Maven tool window can still run goals with
bundled Maven. Downloading a JDK inside IntelliJ does not necessarily put `java`
on the terminal's PATH; configure the local JDK/PATH if using terminal commands.

### 2. Add Mockito test dependencies

Keep these properties in the local POM:

```xml
<maven.compiler.release>27</maven.compiler.release>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
<mockito.version>5.24.0</mockito.version>
```

These entries are already included in this repository's `<dependencies>`.
If adding them to a local POM, update existing entries rather than duplicating them:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>${mockito.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>${mockito.version}</version>
    <scope>test</scope>
</dependency>
```

Reload the Maven project after saving. Keep the existing dependencies and plugins.
Test scope keeps Mockito out of the packaged application's runtime dependencies.

In the existing `<build><plugins>` section, add the dependency plugin and update
the existing Surefire entry, rather than adding a duplicate:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.11.0</version>
    <executions>
        <execution>
            <id>resolve-dependency-paths</id>
            <phase>initialize</phase>
            <goals>
                <goal>properties</goal>
            </goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
    <configuration>
        <argLine>-javaagent:"${org.mockito:mockito-core:jar}"</argLine>
    </configuration>
</plugin>
```

This explicitly loads Mockito's agent for Maven tests. The older Mockito/Byte
Buddy setup failed under Java 27 even when compilation targeted Java 17.
Experimental flags are not needed for the reported passing updated setup.

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

- [x] Java 27 local test runtime confirmed by the supplied output.
- [x] Both Mockito dependencies resolved locally.
- [x] `ConnectionMockTest.java` reported passing locally.
- [x] Real JDBC `MySqlConnectionTest.java` reported passing locally.
- [ ] Verify Maven 3.9+ when standardizing the local build.
- [ ] `mvn clean package` creates `target/banking-cli.jar`.
- [ ] Launching the JAR prints the scaffold notice.

The checked items are based on shared local results. Packaging and launching
the executable JAR are still waiting to be checked.

## Local MySQL connection check

The local test is
`src/test/java/com/example/banking/integration/MySqlConnectionTest.java`.
It opens a connection with `DriverManager.getConnection`, executes `SELECT 1`,
checks the returned value, and closes resources using try-with-resources.

In **Run → Edit Configurations**, verify the **Class** field, not just the
configuration's name:

```text
com.example.banking.integration.MySqlConnectionTest
```

Add these separate **Environment variables**:

| Variable | Local value |
|---|---|
| `BANK_DB_URL` | `jdbc:mysql://localhost:3306/` for the server-only smoke test |
| `BANK_DB_USER` | The local database username |
| `BANK_DB_PASSWORD` | Enter privately; never commit or display it |

Leave **Store as project file** unchecked. Local run settings may store values
in plain text. Rotate exposed credentials, including those shown in screenshots.
Use a dedicated least-privilege account for the application rather than `root`.

`Missing required environment variable: BANK_DB_URL` means execution stopped
before attempting a connection. IntelliJ run-configuration variables do not
automatically apply to terminal commands.

Despite its package name, `MySqlConnectionTest` matches Surefire's default
`*Test` naming, so it also runs during `mvn test` and requires those variables
in the terminal environment. The scaffold's Failsafe profile instead uses `*IT`.
Standardize this split before adding tests that modify data.

A passing check proves connectivity, authentication, and basic SQL execution,
not schema integrity, rollback, locking, or banking behavior.

### IntelliJ settings for the application

The application's factory uses `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`,
not the older smoke test's `BANK_DB_*` names.

1. Open **Run → Edit Configurations**.
2. Select the **Application** configuration for
   `com.example.banking.app.BankingApplication`. If it is missing, use
   **+ → Application** and select that main class and the project module.
3. Use **Modify options → Environment variables** if the field is hidden.
   Open the environment editor and enter one row per setting:
   - `DB_URL`: `jdbc:mysql://localhost:3306/banking_db`
   - `DB_USERNAME`: the application's least-privilege MySQL user
   - `DB_PASSWORD`: that user's password, entered privately
4. Leave **Store as project file** unchecked, then select **Apply → OK**.
   Do not commit local settings containing credentials or share screenshots
   showing their values.

These settings belong to the selected run configuration. They do not
automatically reach another test configuration, the Maven runner, or PowerShell.
The entry point still only prints a starter notice, so saving these settings
does not prove a connection works; it must later call the factory.

`AppConfigTest`, `ConnectionMockTest`, and `ConnectionFactoryTest` do not need
real credentials. For the older real-connection `MySqlConnectionTest`, select
its JUnit configuration and retain the exact variable names its code reads.
Never redirect future mutating integration tests to the application database;
use their dedicated test configuration and database.

## Schema implementation walkthrough

The complete initial script is [sql/mysql/schema.sql](sql/mysql/schema.sql).
It extends the seven-table reference design with the approved supporting
database-design additions, while retaining the requested customer fields and
birth-date triggers. It now defines nine tables:

- The original seven tables remain, with their original lifecycle and
  transaction names, username login, lockout fields, and independent attempt audit.
- `accounts` adds `opening_date`, a savings `interest_rate`, and
  `minimum_balance`. Java must supply the opening date; new rates/minimums
  default to zero rather than silently adopting the guide's sample values.
- `transactions` adds source/destination balance-before snapshots.
- `login_audit` adds optional IP address, device information, and safe failure
  reason, plus reporting indexes. These may be NULL for a local CLI.
- `account_daily_balance` stores one nonnegative closing balance per account/day.
- `transaction_change_audit` records committed ledger INSERT/UPDATE changes using
  two triggers. These rows roll back with the financial transaction and **do not
  replace** independently written financial-attempt audit records.
- The five agreed extra checks enforce nonnegative deposit/loan rates,
  nonnegative after-balance snapshots, and allowed financial-attempt outcomes.

Existing equivalent fields are reused: deposit maturity stays in
`deposit_details`, loan/deposit rates stay in their detail tables, and
`reference_number`/`created_at` keep the original naming. The guide's single
before/after balance is represented per endpoint so transfers are unambiguous.
Optional phone remains optional, without a new unique-phone requirement.

Stored financial procedures, interest functions, scheduled snapshots, sample
inserts, and report executions are **not implemented**. Adding a daily-balance
table does not calculate monthly averages or credit interest. Maturity still
uses the original Java settlement workflow, not an unrestricted credit.
The guide's DELETE audit trigger is omitted: retained ledger-change records
already prevent audited transaction deletion through their foreign key.
See [docs/decisions.md](docs/decisions.md) for the design mappings.

The local `customers` table is already present. Do not run the complete script
over it: this file is for an **empty, explicitly selected database**, not a
migration. It intentionally does not execute CREATE DATABASE, USE, DROP, or
CREATE TABLE IF NOT EXISTS. Execution stops on existing objects rather than
silently accepting an incompatible schema.

Before creating anything, check:

```sql
SHOW DATABASES LIKE 'banking_db';
```

If it exists, inspect `SHOW TABLES FROM banking_db;` and do not overwrite objects.
For a fresh environment only, create/select the database outside the script:

```sql
CREATE DATABASE banking_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE banking_db;
```

Then execute the schema file through Workbench's SQL-script editor, or through
the SQL-mode MySQL client with `SOURCE sql/mysql/schema.sql;` from the project
root. The client must understand DELIMITER for the four trigger definitions.
Use a separately selected empty `banking_test` database for integration testing.

For an existing empty project database, execute `USE banking_db` without
recreating it. Do not blindly rerun table creation, use destructive `DROP`
statements, or expect normal `ROLLBACK` to undo MySQL DDL. Back up existing data.
Do not insert plaintext passwords or real customer data.

Verify the first section with:

```sql
SELECT DATABASE();
SHOW TABLES;
DESCRIBE customers;
SHOW CREATE TABLE customers;
SELECT COUNT(*) AS customer_count FROM customers;
```

Expect `banking_db`, a `customers` table with the reference columns and
constraints, InnoDB, and zero rows in a newly created table. `NOT NULL` does not
reject empty strings. Collation affects uniqueness; record username/email
normalization decisions before implementing registration.

Continue in dependency order: **accounts → deposit_details → loan_details →
transactions → login_audit → transaction_audit → account_daily_balance →
transaction_change_audit → triggers**. See the original reference
DDL below under [Database reference design](#database-reference-design).
All nine extended definitions are in the file; their local installation is not
confirmed. Application persistence remains unimplemented.

For an existing database, first inspect the actual table definitions before
preparing a migration. Existing account rows need an explicit opening-date
backfill policy; do not guess historical dates. New change-audit triggers do not
retroactively audit old transactions. MySQL DDL can commit implicitly, so a
transaction/ROLLBACK wrapper does not make schema migration atomic.

After schema creation, the application URL must select its database, for example
`jdbc:mysql://localhost:3306/banking_db`. The server-only smoke-test URL does not.

## Demo

### Current scaffold demo

The repository's entry point is still a starter program. To see its current
output, install the required JDK and Maven, then package and run it:

```bash
mvn clean package
java -jar target/banking-cli.jar
```

Expected application output, based on the entry point:

```text
Secure Banking CLI — scaffold only.
Banking features are not implemented. See README.md and the supplied design.
```

This starter program does not need database credentials. It only prints the
notice above; it cannot perform banking operations yet. The build has not been
verified here. Use `mvn test` separately to demonstrate the setup tests.

### Local setup demo — reported passing

1. Run `ConnectionMockTest` to demonstrate JUnit/Mockito on Java 27 without MySQL.
2. Run `MySqlConnectionTest` with private environment settings to demonstrate
   real authentication and `SELECT 1`.
3. Show test results without exposing passwords or environment-variable values.

These checks show that the test tools and database connection work. The final
banking demo will come later, once the services and menus are ready.

### Planned final banking demo — not yet implemented

Use synthetic customers and demonstration accounts only. Enter passwords
privately; do not include passwords, hashes, or credentials in screenshots,
recordings, receipts, or documentation.

| Order | Demonstration | Expected result after implementation |
|---|---|---|
| 1 | Apply schema to a clean demo database and launch | Reproducible setup; welcome menu |
| 2 | Register a customer; try a duplicate username/email | Valid registration succeeds; duplicate is rejected |
| 3 | Try wrong/unknown credentials, then log in correctly | Same generic failure message; valid login creates a session |
| 4 | View/edit permitted profile fields; change password | Current password required; new password works |
| 5 | Open Savings and Current accounts and list them | Unique numbers; only owned accounts shown |
| 6 | Deposit 500.00 into Savings; withdraw 100.00 | Savings balance 400.00; one ledger row per committed operation |
| 7 | Transfer 150.00 from Savings to Current | Savings 250.00; Current 150.00; one TRANSFER row |
| 8 | Attempt a 300.00 Savings withdrawal | Rejected; balances and successful ledger unchanged |
| 9 | View history, date/type filters, and pages | References, directions, statuses, stable ordering |
| 10 | Open a Deposit funded from owned Savings/Current | Funding debited atomically; principal locked |
| 11 | Attempt early settlement, then settle a mature test Deposit | Early release refused; mature principal transferred and Deposit closed |
| 12 | Create a Loan; make partial, excessive, and exact repayments | Debt reduced; overpayment refused; exact repayment closes Loan |
| 13 | Try accessing another customer's account details/history | Access denied without private data disclosure |
| 14 | Demonstrate five failed logins and lock expiry | Temporary 15-minute lock; safe authentication audit |
| 15 | Leave an authenticated prompt idle, then submit input | Expired session denied before action; TIMEOUT audited |
| 16 | Log in again and log out | Session cleared; LOGOUT audited |
| 17 | Run rollback/concurrency tests and inspect safe audit evidence | No partial transfers or overspending; failed attempts survive rollback |

For Deposit maturity and lockout/timeout boundaries, use controlled-clock tests
rather than waiting months or modifying real customer records. Use isolated test
fixtures for fault injection and frozen/closed-account cases. No customer-facing
admin menu or intentional failure switch is required.

Run the final automated evidence separately:

```bash
mvn test
mvn verify -Pintegration
```

Tests that create fixtures, change data, or exercise transactions must target
guarded `banking_test`, never the application's database. The explicitly selected
`ConnectionFactoryReadOnlyIT` is only a connection diagnostic: it executes a
fixed SELECT against `banking_db` with no setup, mutations, or cleanup.
Record actual test outcomes when implemented; do not mark this planned
demo as completed before the corresponding features and tests work.

## Planned functionality

- Registration, bcrypt password hashing, login, temporary lockout.
- Profile management and password changes with current-password verification.
- Multiple owned Savings, Current, Deposit, and Loan accounts.
- Deposits, withdrawals, atomic transfers, balances, filtered paginated history.
- Funded term deposits, maturity and settlement, Loan repayment.
- Inactivity timeout, logout, and append-only authentication/financial audit.

### Account rules

| Type | Balance meaning | Deposit operation | Withdrawal | Ordinary transfer | Specialized rule |
|---|---|---|---|---|---|
| Savings | Spendable cash | Yes, owned ACTIVE account | Yes, funds >= amount | Eligible source/destination | Minimum balance 0 by default |
| Current | Spendable cash | Yes, owned ACTIVE account | Yes, funds >= amount | Eligible source/destination | Overdraft limit 0 in baseline |
| Deposit | Locked principal | Opening funding only, from owned eligible Savings/Current | Only through maturity settlement | Not an ordinary endpoint | Fixed maturity date; settle principal into owned eligible Savings/Current, zero balance and close |
| Loan | Outstanding principal owed | No ordinary deposit | Never; not spendable cash | Not an ordinary endpoint | Simplified creation without disbursement; repay from owned eligible Savings/Current; refuse overpayment and close at zero |

Ordinary transfers require an owned source; an eligible destination may belong to
another customer without granting access to its private balance/history.
Deposit funding/settlement use dedicated workflows, not ordinary transfer eligibility.
Loan creation does not disburse money. No interest engine is in scope.

## Full application setup checklist — after implementation

Use this checklist when the full application is ready. It follows sections 8
and 26 of the project guidelines and covers the setup another developer will
need. For now, the local setup tests pass, but the banking services and menus
are still being built.

1. **Verify prerequisites.** Use JDK 27, Maven 3.9+ and a running MySQL 8.4+
   server with InnoDB. Import the root `pom.xml`; do not create a nested project.
2. **Create isolated databases.** Use `banking_db` for application/demo data and
   `banking_test` for automated tests. Do not run integration tests against the
   application database.
3. **Separate setup and runtime privileges.** An authorized setup account creates
   tables and grants privileges. A non-root runtime principal has only the
   necessary DML privileges on `banking_db`; a separate test principal targets
   `banking_test`. Restrict audit tables to required append/read access rather
   than granting blanket UPDATE/DELETE. Any test reset uses an explicitly guarded
   setup/cleanup connection with only the test privileges it needs.
4. **Apply the implemented schema to each database.** Once
   `sql/mysql/schema.sql` contains the reviewed DDL, select the intended database
   explicitly and run the script. For example, in an authenticated MySQL client:

   ```sql
   USE banking_db;
   SOURCE sql/mysql/schema.sql;
   ```

   Run the corresponding setup separately for `banking_test`, ensuring the script
   selects that database instead of a hardcoded `USE banking_db`. The path is
   relative to the client's working directory; use a verified absolute path if
   needed. Do not rerun CREATE TABLE statements over existing data without an
   explicit migration/reset decision.
5. **Configure credentials privately.** Provide `DB_URL`, `DB_USERNAME` and
   `DB_PASSWORD` to the Java process. A local non-secret JDBC URL shape is
   `jdbc:mysql://127.0.0.1:3306/banking_db`. Provide corresponding `TEST_DB_*` values
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

Create isolated `banking_db` and `banking_test` databases with separate principals.
Integration tests should use `TEST_DB_URL`, `TEST_DB_USERNAME`, and
`TEST_DB_PASSWORD`, rejecting missing configuration or application database targets.
No credentials are needed to run the current scaffold.

The local smoke test uses `BANK_DB_*` independently of these planned application
`DB_*` and integration `TEST_DB_*` settings. They are not interchangeable unless
the code explicitly maps them.

The seven reference tables are `customers`, `accounts`, `deposit_details`,
`loan_details`, `transactions`, `login_audit`, and `transaction_audit`. The
approved supporting-design extension adds `account_daily_balance` and
`transaction_change_audit`, for nine tables total.
`sql/mysql/schema.sql` contains initial DDL plus DOB and row-change triggers,
not a data migration. `sql/sample-data.sql` remains an optional placeholder.

### Resource file responsibilities

- `src/main/resources/app.properties` is a non-secret settings file, **not Java**.
- `src/main/resources/logback.xml` is logging configuration, **not Java**.
- `src/main/java/com/example/banking/app/` is a Java package directory containing
  the `.java` entry point, menus and console-input classes.
- `sql/mysql/schema.sql` is MySQL DDL. `sql/sample-data.sql` is optional synthetic
  seed data; the sample-data file remains a placeholder.
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
and SQL in this reference do not implement the placeholder Java classes.
The executable schema is maintained separately in `sql/mysql/schema.sql`.
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

| In scope | Out of scope |
|---|---|
| Registration, profile and password changes | Real money, payment networks, KYC/regulatory workflows |
| Login, hashing, lockout, sessions and logout | Production identity provider, MFA implementation |
| Savings, Current, Deposit and Loan accounts | Interest engines, loan origination/disbursement |
| Deposit, withdrawal, atomic transfer, repayment | Internet-facing API, distributed services, fraud detection |
| Balance, filtered/paginated history, audit | GUI, web framework, Spring Boot, React |
| JDBC, automated tests, reproducible setup | Production banking certification/hardening |

| Role | Responsibility |
|---|---|
| Customer | Register/login; manage own permitted profile fields; open accounts; transact on owned eligible sources; view own data; change password; logout |
| Application | Validate, authorize, hash/verify, manage expiry, coordinate transactions and write ledger/audit |
| Database | Enforce keys/constraints and provide persistence, locking, isolation and atomic commit |
| Trainer/evaluator | Inspect code/schema, run tests and observe security/rollback demonstrations; no customer-facing admin privilege |

#### Functional requirements

| ID / brief | Requirement | Acceptance |
|---|---|---|
| F01 / P1-01 | Registration | Unique username/email; validated fields; password policy; only encoded hash stored |
| F02 / P1-02 | Authentication | Valid credentials create session; invalid credentials give generic audited denial |
| F03 / P1-03 | Profile | View/update permitted fields; password change verifies current password |
| F04 / P1-04 | Account creation | All four types; generated unique account number; owner linked |
| F05 / P1-05 | Multiple accounts | One customer may own many; listings are owner-scoped |
| F06 / P1-06 | Deposit | Positive amount; owned ACTIVE eligible account; balance and ledger agree |
| F07 / P1-07 | Withdrawal | Sufficient funds after row lock and re-read |
| F08 / P1-08 | Transfer | Debit, credit and ledger are atomic; any failed step rolls back |
| F09 / P1-09 | Balance enquiry | Owned account details and balance only |
| F10 / P1-10 | History | Dated, referenced, status-bearing records with stable pagination |
| F11 / P1-11 | Sessions | Idle expiry, invalidation and explicit logout |
| F12 / P1-12 | Integrity | Keys, constraints, prepared statements, DECIMAL money and JDBC transactions |
| F13 / P1-13 | Audit | Login/financial outcomes with reference, timestamp and status |
| F14 / P1-14 | Testing | Automated success, bad input, funds, authorization, rollback and concurrency cases |

#### Non-functional requirements

| Attribute | Target |
|---|---|
| Security | Salted adaptive hashes; no plaintext/reversible passwords; service authorization; prepared SQL; external secrets |
| Integrity | BigDecimal/DECIMAL(19,2); explicit precision rules; locked financial transactions |
| Maintainability | Layered packages; no SQL in menus/services; no banking rules in presentation |
| Reliability | try-with-resources, explicit rollback, typed exceptions, safe connection-loss handling |
| Testability | Injected collaborators and Clock; fake/Mockito service tests; separate real test database |
| Usability | Numbered menus; invalid-input/EOF recovery; safe messages |
| Portability | Standard JDBC; dialect differences documented; only MySQL implemented here |
| Observability | Structured, correlated logs with no passwords, hashes, credentials or full account data |

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

| Use case | Successful flow | Important alternatives |
|---|---|---|
| Register | Validate → hash → save | Duplicate fields, invalid input, weak password |
| Login | Verify → create session → customer menu | Unknown/wrong credentials, locked/closed customer |
| Open account | Select type → validate terms → generate number → save | Invalid type, missing term/principal, invalid funding |
| Deposit/withdraw | Select owned account → validate → transact → receipt | Non-owner, inactive/wrong type, bad amount, insufficient funds |
| Transfer | Resolve → ordered locks → debit/credit/ledger → commit | Self-transfer, bad endpoint, insufficient funds, write failure |
| Repay | Select owned Loan/funding → debit funding → reduce debt | Closed Loan, non-owner, overpayment |
| History | Select owned account → filter → paginate | Unauthorized account, invalid/out-of-range page |
| Logout/timeout | Clear session → audit → welcome | Idle expiry detected when blocked input returns |

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

| Decision | Rule and consequence |
|---|---|
| AD-1 | Plain Java + JDBC, no application framework |
| AD-2 | DAO interfaces separate SQL from use cases and enable isolated tests |
| AD-3 | Services own transactions; participating DAOs receive the same Connection |
| AD-4 | Pessimistic `FOR UPDATE` locks in ascending account-ID order |
| AD-5 | Explicit READ COMMITTED; re-read balances after locking, rather than relying on MySQL's default REPEATABLE READ |
| AD-6 | One ledger row per business transaction with optional endpoints and balance snapshots |
| AD-7 | Separate append-only audit; failed attempts never appear as SUCCESS ledger rows |
| AD-8 | PasswordHasher abstraction; bcrypt cost 12 baseline; original document permits Argon2id/PBKDF2 alternatives |
| AD-9 | In-memory sessions and injected Clock |
| AD-10 | Properties for non-secrets; environment variables for secrets |

| Package | Owns | Must not do |
|---|---|---|
| `app` | Startup, menus, input parsing, rendering, boundary requests/DTOs | SQL or financial business rules |
| `model` / `model.policy` | State, VOs, enums, invariants, pure policies | Console I/O or JDBC |
| `service` | Use cases, authorization, transaction boundaries, event publication | Build SQL or read terminal input |
| `dao` / `dao.jdbc` | Repository contracts, prepared SQL, ResultSet mapping | Independently commit caller work or decide banking policy |
| `security` | Hashing and session lifecycle | Log secrets |
| `validation` | Field/input validation | Access the database |
| `exception` | Typed, user-safe failures | Expose SQL/internal details to users |
| `util` | Config, connections, transaction helper, references, logging | Own banking rules |

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

| Package | Types |
|---|---|
| `app` | BankingApplication, MainMenu, LoginMenu, CustomerMenu, ConsoleInput |
| `model` | Customer, abstract Account, SavingsAccount, CurrentAccount, DepositAccount, LoanAccount, BankTransaction, LoginAudit, TransactionAudit, Money, AccountNumber |
| `model` enums | AccountType, AccountStatus, CustomerStatus, TransactionType, TransactionStatus, AuditOutcome, LoginOutcome |
| `model.policy` | AccountEligibilityPolicy, TransferPolicy, PasswordPolicy, LockoutPolicy |
| `service` | AuthService, CustomerService, AccountService, TransactionService, SessionService |
| `dao` | CustomerDAO, AccountDAO, TransactionDAO, LoginAuditDAO, TransactionAuditDAO |
| `dao.jdbc` | JdbcCustomerDAO, JdbcAccountDAO, JdbcTransactionDAO, JdbcLoginAuditDAO, JdbcTransactionAuditDAO |
| `security` | PasswordHasher, BcryptPasswordHasher, SessionContext, SessionManager |
| `validation` | InputValidator, CustomerValidator, AccountValidator, TransactionValidator |
| `exception` | BankingException, ValidationException, AuthenticationException, AuthorizationException, InsufficientFundsException, AccountNotEligibleException, PersistenceException, SessionExpiredException |
| `util` | AppConfig, ConnectionFactory, JdbcTransaction, ReferenceGenerator, LoggerFactory |
| Step 5 value types | TransactionReference and PasswordHash (tests confirmed locally on Java 27) |
| Later DDD additions | Username, Email, DepositTerms, LoanTerms; AccountNumberGenerator responsibility; domain events and EventPublisher |

Account holds shared identity, owner, status and invariants; use subclassing only
for genuinely different behavior. No public balance setter is permitted.
Money supplies `add`, `subtract`, `isPositive`, `compareTo`, value equality and
immutable results. BankTransaction is immutable and created through factories
that enforce legal endpoint shape. PasswordHasher exposes
`String hash(char[] raw)` and `boolean verify(char[] raw, String encoded)`;
callers clear password arrays after use. Audit entities are append-only.

#### Service dependencies

| Service | Collaborators |
|---|---|
| AuthService | CustomerDAO, LoginAuditDAO, PasswordHasher, SessionManager, CustomerValidator; password/lockout policy |
| CustomerService | CustomerDAO, CustomerValidator |
| AccountService | AccountDAO, CustomerDAO, AccountValidator, ReferenceGenerator; eligibility policy |
| TransactionService | AccountDAO, TransactionDAO, TransactionAuditDAO, JdbcTransaction, TransactionValidator, ReferenceGenerator, Clock |
| SessionService | SessionManager, Clock |

#### Illustrative service contracts

| Method | Preconditions | Result |
|---|---|---|
| `register(request)` | Valid unique fields and compliant password | ACTIVE customer with hash; validation failure otherwise |
| `authenticate(username, password)` | Nonblank input; DB available | Session on success; generic audited AuthenticationException otherwise |
| `changePassword(session, current, new)` | Active session; current password verifies; valid new password | Hash replaced; audit event; session retained |
| Profile view/update | Active session; allowed fields only | Current customer's safe profile |
| `openAccount(customerId, type, params)` | Session-derived identity and valid type-specific parameters | Unique owned account with correct initial balance/terms |
| `getOwnedAccounts(customerId)` | Identity derived from session | Only owned accounts |
| `deposit(customerId, accountId, amount)` | Owned ACTIVE Savings/Current; valid amount | Balance and DEPOSIT row committed together |
| `withdraw(customerId, accountId, amount)` | Same eligibility; enough funds under lock | Debit and WITHDRAWAL row, or no change |
| `transfer(customerId, sourceId, destinationNumber, amount)` | Owned source, distinct eligible ACTIVE endpoints | Atomic debit/credit/TRANSFER or full rollback |
| `repayLoan(customerId, fundingId, loanId, amount)` | Both owned; eligible funding; ACTIVE Loan; repayment <= debt | Debit funding/reduce Loan; LOAN_REPAYMENT; close at zero |
| `history(customerId, accountId, filter, page)` | Owned account; valid page/size | Ordered rows with CREDIT/DEBIT direction |
| `isExpired(session, now)` / `requireActive(session)` | Session timestamps and configured idle duration | Expire/invalidate/audit, or refresh valid activity |

#### DAO contracts

All user values use PreparedStatement parameters.

| DAO | Operations |
|---|---|
| CustomerDAO | Insert; find by ID/username; username/email existence; profile/hash updates; failed-login increment/reset; locked-until update |
| AccountDAO | Insert; owned lookup by ID; lookup by number; list by customer; ordered `lockForUpdate`; balance/status updates; type-detail persistence |
| TransactionDAO | Insert on caller connection; account history/filter/page; find by reference |
| LoginAuditDAO | Append customer ID if known, attempted username and outcome using own short transaction |
| TransactionAuditDAO | Append customer, reference, event type, outcome and failure code outside the money transaction |

### Database reference design

**Source sections 11 and 17.** Seven tables are required. Customers have zero or
many accounts, each account exactly one customer. Accounts have zero or many
source/destination transactions and zero or one relevant type-detail row.
Known customers have login-audit rows; unknown login attempts have null customer
ID. Transaction audit links logically by reference, not a mandatory ledger FK:
failed attempts have no committed ledger row.

#### Complete MySQL reference DDL

The following reproduces the reference schema's columns, constraints and indexes
with formatting changes. It is the original reference, not the customized
customer definition: the executable `sql/mysql/schema.sql` uses separate names,
customer code, DOB/address, enum customer status, and DOB triggers as requested.
The current project targets MySQL 8.4+ with InnoDB; verify CHECK enforcement and
strict SQL mode when installing.

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

| Business rule | Enforcement |
|---|---|
| Unique username/email/account/reference | UNIQUE constraints plus friendly prechecks |
| Nonnegative balance; positive amount; allowed statuses/types | CHECK constraints plus model/input validation |
| Legal ledger endpoints | `chk_txn_shape` and transaction factories |
| Referential integrity | FKs |
| Ownership | Session identity and `WHERE account_id=? AND customer_id=?`; FKs alone do not authorize |
| Type eligibility, maturity, repayment limits | Domain policies and services under row locks |

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

| Account state | Behavior |
|---|---|
| CREATED | In-memory only; valid persisted account becomes ACTIVE |
| ACTIVE | Operations depend on account type, ownership and funds |
| FROZEN | No customer financial actions; unfreeze only via explicitly authorized trainer/admin script, not a customer admin menu |
| MATURED | Deposit only; settlement/closure permitted |
| CLOSED | Terminal; no new transactions; closure requires zero balance/debt |

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

| Input/action | Required checks |
|---|---|
| Registration | Required fields, email, username, password, uniqueness; no partial customer |
| Login | Required credentials, active/unlocked customer, hash verification; generic failure |
| Amount | BigDecimal parse; > 0; scale <= 2; configured maximum |
| Account | Exists; session ownership where required; eligible status/type |
| Withdrawal | Sufficient balance after locking and re-read |
| Transfer | Distinct endpoints, eligible accounts, owned source; atomic failure |
| Pagination | Page >= 0; size 1–50; stable ordering; reject unsafe extremes |
| CLI | Empty/nonnumeric input, EOF, repeated invalid input; after three failed prompt attempts return safely to menu |

#### Exceptions and user-safe messages

All listed exceptions extend the RuntimeException-based BankingException.

| Exception | User response | Logging/audit |
|---|---|---|
| ValidationException | Specific safe field hint | WARN without secrets; audit optional |
| AuthenticationException | “Invalid username or password.” | INFO outcome; FAILURE/LOCKED audit |
| AuthorizationException | “Account not found.” | WARN with internal IDs; REJECTED financial audit |
| SessionExpiredException | Return to welcome and require login | TIMEOUT audit |
| AccountNotEligibleException | Safe inactive/wrong-type explanation | Safe outcome/context |
| InsufficientFundsException | “Insufficient funds.” | INFO; REJECTED audit |
| PersistenceException | “The operation could not be completed. Please try again.” | Restricted ERROR/cause; FAILED/ROLLED_BACK audit |

Do not print SQL or stack traces to users. Restricted technical exception logs
must still avoid credentials, hash/password values and full account numbers.
For uncertain commits, use an outcome-aware response rather than encouraging a
blind retry.

#### Configuration reference

| Setting | Source | Default |
|---|---|---|
| DB_URL, DB_USERNAME, DB_PASSWORD | Private environment | Required once persistence is implemented |
| TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD | Private test environment; project isolation convention | Dedicated guarded banking_test |
| session.idle.timeout.seconds | app.properties | 300 |
| db.query.timeout.seconds | app.properties | 10 |
| tx.max.amount | app.properties | 1000000.00 |
| auth.max.failed.logins | app.properties | 5 |
| auth.lock.minutes | app.properties | 15 |
| password.hash.algorithm | app.properties | bcrypt |
| password.hash.cost | app.properties | 12 |
| history.max.page.size | app.properties | 50 |

#### Audit and logging

| Event | Destination | Fields |
|---|---|---|
| Login success/failure/locked/timeout/logout | login_audit | Known customer ID or null, attempted username, outcome, timestamp |
| Financial success/rejection/failure/rollback | transaction_audit | Customer, reference, event type, outcome, safe failure code, timestamp |
| Technical diagnostics | SLF4J/Logback application log | Level, class, reference, safe exception details |

Audit writers append only; no update/delete API. Mask account numbers to last
four characters in logs. Never log credentials, raw passwords or hashes.
Failed money attempts audit after rollback in a separate short transaction so
their evidence survives. Audit is not a substitute for the committed ledger.

### Domain-driven design

**Source sections 28–35.** Use lightweight strategic/tactical DDD, not a
distributed architecture. Core subdomain: account rules and ledger/money movement.
Supporting: identity/access. Generic: audit/logging.

#### Shared language

| Term | Meaning |
|---|---|
| Customer | Registered person who authenticates and owns accounts |
| Credentials | Username/password input; only encoded hash is stored |
| Session | Authenticated activity ending in logout or idle expiry |
| Account/type | Owned Savings, Current, Deposit or Loan record |
| Balance | Cash for Savings/Current/Deposit; debt for Loan |
| Money | Immutable decimal amount with two places; operation amount positive |
| Deposit operation | Adding ordinary cash, distinct from a term Deposit account |
| Withdrawal | Removing eligible available funds |
| Transfer | Atomic movement between endpoints; ordinary transfers differ from term funding/settlement |
| Repayment | Reducing Loan debt from owned eligible funds |
| Ledger entry | Immutable permanent record of a committed money movement |
| Reference | Unique system-generated transaction identifier |
| Frozen/Matured/Closed | Lifecycle states with explicit operation restrictions |
| Audit | Append-only record of an attempt or outcome |

#### Bounded contexts

| Context | Owns / exposes | Relationship |
|---|---|---|
| Identity & Access | Customer, credentials, session, lockout; customerId and login events | Upstream Customer–Supplier for Account Management |
| Account Management | Account aggregate, lifecycle and eligibility; IDs/numbers/queries | Shares small AccountId/Money kernel with Ledger |
| Ledger & Transactions | Immutable ledger, transfer coordination, history; movement events | Coordinates locked account mutations within one database transaction |
| Audit & Observability | Append-only login/financial records and logs | Conformist event subscriber; no banking decisions |

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
  Section 31's `AuditOutcome` is a financial-attempt enum: `SUCCESS`,
  `REJECTED`, `FAILED`, `ROLLED_BACK`. It matches `transaction_audit.outcome`,
  not login-audit outcomes or ledger statuses.
  `LoginOutcome` separately represents `SUCCESS`, `FAILURE`, `LOCKED`,
  `TIMEOUT`, `LOGOUT` for `login_audit.outcome`.

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

| Events | Timing / consumer |
|---|---|
| CustomerRegistered | After registration commit; audit optional |
| LoginSucceeded, LoginFailed, AccountLockedOut | Auth outcome; login audit |
| SessionExpired, LoggedOut | Session invalidation; login audit |
| AccountOpened | After account commit; audit optional |
| MoneyDeposited, MoneyWithdrawn | After successful commit; financial audit |
| TransferCompleted | After commit; financial audit |
| TransferRejected, TransferFailed | After rejection/rollback; independent financial audit |
| LoanRepaid, LoanClosed | After repayment commit / debt reaches zero; financial audit |

Events are small immutable past-tense objects, synchronously published in-process.
Do not publish success before commit, audit failure within the rolled-back unit,
or double-write an outcome through both direct and event-based audit paths.

#### Business invariants

| ID | Invariant | Verification |
|---|---|---|
| INV-01 | No plaintext/reversible password stored | T01 |
| INV-02 | No unauthorized read/change of another customer's accounts; normal transfer credit is allowed without exposing destination data | T06 |
| INV-03 | Operation amount > 0, scale <= 2 | T13 |
| INV-04 | Withdrawal/transfer never creates a negative balance | T08, T14 |
| INV-05 | Transfer all-or-nothing | T09, T10 |
| INV-06 | SUCCESS ledger only for committed movement | T10 |
| INV-07 | Loan not spendable; repayment never increases/exceeds principal | T17 |
| INV-08 | Deposit principal released only at maturity | T16 |
| INV-09 | Frozen/closed do not transact; matured allows Deposit settlement only | T11 and account-policy tests |
| INV-10 | Expired/logged-out session cannot perform protected operations | T12 and logout tests |
| INV-11 | Audit append-only and secret-free | Audit/security tests and log inspection |
| INV-12 | Account numbers/references unique | Generator and UNIQUE integration tests |

### Testing and traceability

**Source sections 25 and 36–37.**

| Level | Coverage / evidence |
|---|---|
| Unit | Validators, Money, policies, password rules, fake-Clock expiry; JUnit reports |
| Service | Duplicate registration, ownership, funds, transfer and repayment rules; Mockito/fake DAOs |
| Mocked JDBC | Connection/PreparedStatement/ResultSet binding, mapping, cleanup and failure paths |
| DAO integration | Real CRUD, FK/UNIQUE/CHECK, mappings, rollback; repeatable guarded banking_test setup/teardown |
| Transaction | Success, failures after debit/credit/ledger, concurrent withdrawals/opposite transfers; assert final balances and rows |
| Security | Hash-only storage, invalid login, ownership, SQL injection, timeout/logout |
| Acceptance | Clean schema → register → login → accounts → deposit/withdraw/transfer → history → timeout/logout |

The document lists container-based testing as optional, not a requirement.
This plan uses a dedicated MySQL test database. Mocks cannot substitute for real
locking, isolation or SQL tests. Use `*Test.java` for Surefire and `*IT.java` for
the existing Failsafe profile: `mvn test`, then `mvn verify -Pintegration`.
The PDF's simpler `mvn test` instruction does not by itself run this project's
separately configured integration profile.

#### Essential tests T01–T18

| ID | Scenario | Required result |
|---|---|---|
| T01 | Valid registration | Customer saved with encoded hash, no plaintext |
| T02 | Duplicate username/email | Rejected; existing data unchanged |
| T03 | Correct login | Session and customer menu |
| T04 | Wrong password/unknown username | Same generic denial; failure audited |
| T05 | Five consecutive failures | Temporary lock; LOCKED audit |
| T06 | Another customer's account | No unauthorized access/data leakage |
| T07 | Positive deposit | Balance and ledger consistent |
| T08 | Withdrawal above funds | No balance or ledger change |
| T09 | Eligible transfer | Equal debit/credit; exactly one SUCCESS row |
| T10 | Forced failure after debit | Both balances restored; no SUCCESS row; failure audit survives |
| T11 | Self/frozen/closed transfer | Rejected without mutation |
| T12 | Idle past timeout | Session invalid; next protected action requires login |
| T13 | Malformed/negative/three-decimal amount | Safe rejection; no database mutation |
| T14 | Parallel withdrawals and opposite transfers | No overspend; bounded deadlock handling |
| T15 | Pagination boundary | Stable ordering; no duplicate/omitted rows in fixed dataset |
| T16 | Early/mature Deposit settlement | Early refused; mature succeeds |
| T17 | Partial/exact/excess Loan repayment | Debt reduced; exact closes; overpayment refused |
| T18 | SQL-injection text inputs | Bound as data or safely validated; no SQL structural effect |

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

| Requirements | Design | Evidence |
|---|---|---|
| F01–F02 | AuthService, CustomerDAO, PasswordHasher, LoginAuditDAO, LockoutPolicy | T01–T05, stored-hash inspection |
| F03 | CustomerService, AuthService, CustomerDAO | Profile/password unit/service tests |
| F04–F05 | Account hierarchy, AccountService, details tables | T06/T16/T17, multiple-account creation/listing |
| F06–F07 | TransactionService, AccountDAO, TransactionDAO | T07–T08 |
| F08 | Shared transaction, locks, retry | T09–T11/T14 |
| F09–F10 | AccountService, TransactionDAO history | Owner-scoped balance tests, T15 |
| F11 | SessionService, SessionManager, Clock | T12, logout |
| F12 | Constraints, prepared SQL, JdbcTransaction | T13/T18, DAO tests |
| F13 | Audit DAOs, events, safe logs | Audit-table and redaction tests |
| F14 | Complete layered test suite | Executed reports and coverage review |

### Milestones, evaluation and submission

**Source sections 26–27 and 37–41.** For the detailed work order see
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). The updated local setup uses JDK 27,
Maven 3.9+, MySQL 8.4+, application/test databases, least-privilege credentials,
the implemented schema, private environment configuration and all required
Maven dependencies. Then test, package and launch with the commands above.
No database prerequisite is needed for the current scaffold-only entry point.

| Milestone | Work | Exit criteria |
|---|---|---|
| P1-M1 | Requirements, use cases/class/ER diagrams, repository, schema skeleton | Design/repository ready; database connects |
| P1-M2 | Registration, hashing, login, connections | Positive/negative login tests; Week 2 review |
| P1-M3 | Profile, accounts/listing; all four types represented | Ownership/type/status validation |
| P1-M4 | Deposit, withdrawal, balance | BigDecimal and invalid/funds checks; Week 3 interim |
| P1-M5 | Atomic transfer, rollback, history | Rollback/concurrency tests pass |
| P1-M6 | Session, validation, exceptions, audit, Deposit/Loan | No secrets logged; timeout/logout and specialized flows work |
| P1-M7 | Test completion, README, demo | Reproducible clean setup; Week 3 final evaluation |

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

| Area | Marks | Assessment |
|---|---|---|
| Core Java, OOP and code quality | 25 | Classes, interfaces, collections, enums, maintainability |
| JDBC, SQL and schema | 20 | DAO separation, constraints, prepared statements, indexing |
| Banking and transaction integrity | 25 | Operations, transfers, rollback, ledger and account-type rules |
| Exceptions, validation and security | 15 | Hashing, authorization, timeout, safe input/errors |
| Testing, Git and documentation | 15 | Meaningful coverage/commits, README, reproducibility |
| **Total** | **100** | Mandatory gates still apply |

#### AI-assisted development and participant rotation

The source assigns approximately half the cohort to Group A (AI-assisted P1/P2)
and half to Group B (primarily independent P1/P2), then rotates participants to
designated associates. Odd-cohort allocation follows training policy. This
describes the course arrangement, not a claim about this participant's group.

AI may explain JDBC errors, generate boilerplate, suggest tests and review code.
Participants must verify generated SQL/security/transaction behavior and record
assistance in README or a short development log. Each participant must explain
hashing, authorization and rollback and may receive a live coding/change task
(for example, reject a closed destination or add history filtering). Maintain
individual assessment records; understanding cannot depend on another
associate's implementation.

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

| Detail | Required clarification before its feature is completed |
|---|---|
| Username/email uniqueness | Normalization, case sensitivity and matching MySQL collation behavior |
| Password character classes | Precisely which mixed classes are required; bcrypt-compatible UTF-8 maximum |
| Account numbers | Chosen: 24 synthetic digits with Luhn; secure generation plus MySQL UNIQUE. Persistence collision handling remains later work; see the identifier walkthrough. |
| Deposit terms | Minimum principal, allowed term range and date/time-zone policy |
| Loan terms | Allowed principal/tenure range and simplified account-creation rules |
| Profile edits | Explicit permitted fields and whether username/email changes are supported |
| Timed lockout | How status and locked_until interact when the temporary lock expires |
| Password-change audit | Event/storage mapping; the reference login_audit CHECK has no PASSWORD_CHANGED outcome |
| Pagination | Choose/document rejection versus clamping; define behavior under new history inserts |
| Audit unavailability | What is reported/reconciled after a primary commit if separate audit persistence fails |
| Commit uncertainty | Preserve references; reconcile before any retry that could duplicate a movement |

The plan recommends three **total** transaction attempts, invalid-page-size
rejection, session checks before activity refresh, and a single reference across
retries. These are explicit implementation interpretations of the source's
integrity requirements, not additional banking features.

#### Source coverage index

| Document sections | README coverage |
|---|---|
| Control/conventions, 1–5, 9 | Document control; purpose, scope, roles, requirements and account rules |
| 6–8 | Architecture, decisions, component/deployment and diagram guide |
| 10–12 | Authentication, database and shared transaction boundary |
| 13–16 | Structure, class inventory, packages, service/DAO contracts |
| 17–18 | Complete reference DDL, constraints, portability and workflows |
| 19–24 | Lifecycle, sessions, validation, exceptions, config and audit/logging |
| 25–27 | Test strategy/cases, build guidance and milestones |
| 28–35 | DDD language, contexts, aggregates, policies, events, repositories and invariants |
| 36–38 | Traceability, acceptance gates and rubric |
| 39–41 | AI/governance, submission and future scope |

## Next steps

The database tables and triggers are finished locally, and all 38 guided unit
tests have passed. The read-only factory connection test also completed locally
with exit code 0 and no reported errors. Configuration validation, mocked
connection checks, and initial live Java connectivity are complete for this
increment. This does not yet prove financial transactions or locking.

1. Step 4 is complete locally: configuration tests, connection checks, six
   logging tests, and the manual startup demonstration succeeded.
2. Your enum, TransactionReference, PasswordHash, and AuditOutcome tests are
   confirmed passing locally on Java 27. The **Financial audit record
   walkthrough** and **Login outcome walkthrough** are now verified together.
   For reference, the successful combined command was:
   `mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest,TransactionAuditTest,LoginOutcomeTest" test`.
   The shared local result confirms 105 passing tests, with no failures,
   errors, or skips. This focused checkpoint replaces
   the separate 97-test and 85-test commands, each of which omits one increment.
   Do not repeat it unchanged. LoginAudit is still a placeholder; its model
   and tests are the next audit-model candidate. Other value types and
   financial services remain separate increments.
3. Keep `ConnectionFactoryReadOnlyIT` as a read-only diagnostic, run explicitly
   from its JUnit configuration with private `DB_*` settings. Its `IT` suffix
   keeps it out of ordinary `mvn test` runs. No repeat connection check is
   needed unless the connection settings or code change.
4. Later tests that change data must use separate `TEST_DB_*` credentials and
   guarded `banking_test`. Initial connectivity is not evidence of transaction
   correctness, constraints, or locking.

### Using the connection factory

The default factory reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when it is
created. It only attempts a connection when `getConnection()` is called.
The code that receives the connection must close it, normally with
try-with-resources:

```java
AppConfig.load();
ConnectionFactory factory = ConnectionFactory.fromEnvironment();

try (Connection connection = factory.getConnection()) {
    // Later JDBC code uses this connection.
}
```

This is a usage example, not a change to the application's entry point. Keep SQL
in the JDBC layer. Transaction isolation, commit/rollback, and retries belong
to the later service transaction helper, not the factory. Statement query
timeouts are applied when preparing statements; the factory does not interpret
`db.query.timeout.seconds` as a connection timeout.

Keep the existing application database. Use a separate test database for any
checks that create, update, or delete data.

Follow [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) and record decisions in
[docs/decisions.md](docs/decisions.md). Write tests incrementally;
cover the source document's T01–T18 scenarios, especially authorization, rollback,
concurrent operations, Deposit maturity, and Loan overpayment.

## AI-assistance log

AI assistance has been used to organize the project, prepare the Maven setup,
explain the implementation steps, and maintain these documents. It has also
helped with Java 27 and Mockito configuration, MySQL setup, schema design, and
configuration-validation examples.

The local setup checks and the completion of the database tables and triggers
were confirmed during development. The user subsequently shared a successful
35-test Money/account-number/reference result. The enum review compared the
five existing enums with the original guidelines (sections 17.1, 21, and 31)
and the checked-in MySQL DDL; no mismatch was found. AI assistance added ten
enum contract tests and the file-by-file guidance below. The focused combined
run passed all 45 tests here on Java 21 with a command-line-only release override.
You subsequently confirmed the enum tests pass locally on Java 27.
AI assistance then added `TransactionReference` and 12 deterministic tests,
after that confirmation. The expanded 57-test run passed in the workspace on
Java 21 with a command-only release override. You have now confirmed the
TransactionReference tests pass locally on Java 27. AI assistance then added
`PasswordHash` and 12 tests; the 69-test focused run passed here on Java 21
with the same command-only override. You subsequently confirmed PasswordHash
tests pass locally on Java 27. AI assistance then added the financial
`AuditOutcome` enum and eight tests, including read-only schema-text agreement.
The focused 65-test run passed on Java 21 before syncing PasswordHash;
the expanded combined run subsequently passed all 77 tests on Java 21.
You have now confirmed the AuditOutcome tests pass locally on Java 27.
AI assistance then replaced only the TransactionAudit model placeholder and
added 20 deterministic construction, nullability, immutable-state, rendering,
and read-only schema-text tests. The 97-test focused suite passed in the
workspace on Java 21.0.9/Maven 3.8.6 with a command-only release override.
AI assistance then added the separate `LoginOutcome` enum and eight tests;
all 85 focused tests passed here on Java 21 with the command-only compiler
override (excluding TransactionAuditTest). The subsequent local combined
run including both TransactionAudit and LoginOutcome passed all 105 tests.
You have since confirmed LoginOutcomeTest passes locally on Java 27.
The initial LoginAudit draft and 16 tests passed 121 focused workspace tests
on Java 21.0.9 with a command-only release override. When syncing, the main
project already contained a six-field LoginAudit and 20 tests used by its
independent writer. Those were preserved, including blank-attempt evidence
and controlled failure reasons. The numbered walkthrough below now matches
that model, not the superseded draft.
No POM downgrade was made. Banking
workflows have not been run in this repository. `AppConfig`, its 28-test
class, and the connection factory with nine Mockito tests are included here.
The Maven configuration now matches the selected Java 27 and dependency versions.
Other examples may still exist only in the
local project or walkthrough. Verify each change locally before proceeding.

The requirements, design notes, reference SQL, and test scenarios in this README
come from the project guidelines and the agreed supporting-document additions.
They describe what the application needs to do, not features that are all
finished. Security, SQL, and transaction-handling code must be reviewed and tested
as each feature is built.

### Enum walkthrough: check persisted names

**Prerequisite met:** you shared a successful Money/account-number/reference
run: **35 tests, 0 failures, 0 errors, 0 skipped**. Keep that work.
This next increment checks the existing enums; it does not add banking
workflows or change your database.

#### 1. Check these five files in IntelliJ

Open `src/main/java/com/example/banking/model/` and compare each file with the
included version. They already match the project guidelines and reference SQL:

| File | Constants that must remain |
|---|---|
| `AccountType.java` | `SAVINGS`, `CURRENT`, `DEPOSIT`, `LOAN` |
| `AccountStatus.java` | `ACTIVE`, `FROZEN`, `MATURED`, `CLOSED` |
| `CustomerStatus.java` | `ACTIVE`, `LOCKED`, `CLOSED` |
| `TransactionType.java` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `LOAN_REPAYMENT` |
| `TransactionStatus.java` | `PENDING`, `SUCCESS`, `FAILED`, `REVERSED` |

No changes are needed if your files match. Do not add `CREATED` to
`AccountStatus`: it describes an in-memory account before persistence.
Do not remove `MATURED`. The transaction statuses preserve the reference
schema; they do not implement new workflows. The baseline ledger still uses
`SUCCESS` only for committed movements. There is no `MATURITY` transaction
type; Deposit settlement uses `TRANSFER`.

#### 2. Add the test file

In `src/test/java/com/example/banking/unit/`, create the Java class
**`EnumContractTest`**. Copy the entire included
`src/test/java/com/example/banking/unit/EnumContractTest.java`, including its
package declaration and imports. Keep all existing tests.

The ten tests protect the five exact name sets, exclude `CREATED`, check
name-based round trips and invalid spellings, compare all five named SQL
CHECK lists, and separately check the customer status ENUM declaration.
Persisted values use enum names, never ordinal numbers or display labels.
Declaration order is not part of this contract.

Keep `sql/mysql/schema.sql` in its existing location under the Maven project
root. The test reads that file as text only; **do not execute the script**.
It neither connects to MySQL nor checks or changes your local tables.
No credentials or environment variables are needed.

#### 3. Run the tests from the project root

In IntelliJ's Terminal, in the directory containing `pom.xml`:

```shell
mvn -version
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest" test
```

Check that Maven reports **Java 27**. Expect **45 tests, 0 failures,
0 errors, 0 skipped**, and **BUILD SUCCESS**.
To run just the new class, use `mvn "-Dtest=EnumContractTest" test`;
that should run **10 tests**.
If you use the editor's green Run arrow instead, set the JUnit configuration's
working directory to the Maven root so it can find `sql/mysql/schema.sql`.
If that file cannot be found, fix its path/working directory rather than
skipping the test or running SQL against your existing database.

**Verification status:** all 45 focused tests passed here using the available
Java 21 toolchain with a command-only `-Dmaven.compiler.release=21` override.
The POM still targets Java 27 and no dependencies were changed.
You have now confirmed the enum tests pass locally on Java 27, meeting the
prerequisite for the transaction reference walkthrough below.
Other step 5 value types remain pending.

### Transaction reference walkthrough

This increment implements the immutable value type from sections 29/31.
It does not add persistence or transaction workflows.

#### 1. Add these files in IntelliJ

Open the Maven project root containing `pom.xml`. In the Project pane, right-click
the appropriate package, choose **New → Java Class**, and copy each included
file into the matching path:

| File | What to do |
|---|---|
| `src/main/java/com/example/banking/model/TransactionReference.java` | Add `TransactionReference` in `com.example.banking.model`. Its constructor validates input, `value()` returns the exact stored text, and equality/hash code use that text. |
| `src/test/java/com/example/banking/unit/TransactionReferenceTest.java` | Add `TransactionReferenceTest` in the test package. Its 12 tests cover format, null/blank/malformed input, equality, SQL-length boundaries, calendar dates, and deterministic generator compatibility. |
| `src/main/java/com/example/banking/util/ReferenceGenerator.java` | Keep unchanged. It still returns a `String`; wrap that result with the new value type. No caller or generator signature changes are required. |
| `README.md` | Keep this file-by-file walkthrough and verification status. |
| `IMPLEMENTATION_PLAN.md` | Keep step 5's current increment and pending local check. |
| `docs/decisions.md` | Keep the reference format, date validation, and scope decisions. |

Keep the existing tests, Maven dependencies, Java 27 target, and SQL files.
Reload Maven in IntelliJ if needed; no new dependency is required.

#### 2. Understand the accepted value

The exact format is `TX-YYYYMMDD-` followed by **28 uppercase ASCII hex digits**:
exactly **40 characters**, fitting both existing `reference_number VARCHAR(40)`
columns. The date must be a real ISO calendar date, including leap-year rules.
Years `0000` through `9999` match the existing generator's supported range;
year zero follows Java's proleptic ISO calendar. There is no current-day check:
a reference can be loaded later and is not the authoritative ledger timestamp.

Nulls, whitespace, lowercase letters, Unicode lookalikes, invalid dates, and
wrong lengths throw `IllegalArgumentException`. Nothing is trimmed, uppercased,
or repaired. All-zero hex is structurally valid; the value object cannot prove
how an input was generated or guarantee uniqueness.

```java
TransactionReference reference =
        new TransactionReference(generator.generateTransactionReference());
String storedReference = reference.value(); // For future JDBC binding.
TransactionReference loaded = new TransactionReference(storedReference);
```

Here `generator` is the existing `ReferenceGenerator` instance. `toString()`
returns the reference for correlation; this is not authorization or proof of
ownership/existence. MySQL's existing UNIQUE constraint remains the final
ledger collision guard. Generate once and retain the same reference across
later retries and audit attempts. No save path is wired in this increment;
future DAO/service code must construct the value type before persistence.

#### 3. Run locally on Java 27

In IntelliJ's Terminal at the Maven root:

```shell
mvn -version
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest" test
```

Confirm Maven reports **Java 27**. Expect **57 tests, 0 failures, 0 errors,
0 skipped**, and **BUILD SUCCESS**. For only the new class, run
`mvn "-Dtest=TransactionReferenceTest" test` and expect **12 tests**.
The combined run reads the checked-in SQL as text for the enum tests; set the
working directory to the project root if using IntelliJ's Run arrow.
No database connection, credentials, schema scripts, or environment variables
are needed. Do not run database setup over your existing data.

**Verification status:** you confirmed the earlier enum tests pass locally on
Java 27. All 57 focused tests, including the 12 new reference tests, passed in
the workspace on Java 21/Maven 3.8.6 with `-Dmaven.compiler.release=21` for that
command only. That workspace run is not Java 27 verification. You have since
confirmed the TransactionReference tests pass locally on Java 27, meeting the
prerequisite for the PasswordHash increment below. The POM and generator are unchanged.

### Password hash walkthrough: continue step 5

#### 1. Add the files in IntelliJ

Your local Java 27 TransactionReference confirmation meets the prerequisite.
In the Project pane, open the following packages, right-click each package,
and choose **New → Java Class**. Use the checked-in source for each file;
keep package declarations and do not create a second `src` folder.

| File | What to do |
| --- | --- |
| `src/main/java/com/example/banking/model/PasswordHash.java` | Add final `PasswordHash` in `com.example.banking.model`, with a private final String, constructor validation, `value()`, exact equality/hash code, and constant redacted `toString()`. |
| `src/test/java/com/example/banking/unit/PasswordHashTest.java` | Add `PasswordHashTest` in `com.example.banking.unit`. Its 12 tests use a fixed public fixture, not real credentials or random hashing. |
| `src/main/java/com/example/banking/security/PasswordHasher.java` | Inspect only. Keep `hash(char[])` and `verify(char[], String)` unchanged. |
| `src/main/java/com/example/banking/security/BcryptPasswordHasher.java` | Inspect only. It remains an explicit unsupported-operation scaffold; do not wire authentication yet. |
| `README.md` | Keep this walkthrough, file tree, and verification status current. |
| `IMPLEMENTATION_PLAN.md` | Follow the PasswordHash increment in step 5; leave other value types pending. |
| `docs/decisions.md` | Read the supported-encoding and output-safety decision. |

No dependency, SQL, Customer, DAO, service, login, registration, or password-policy
changes are needed.

#### 2. Understand the validation and output contract

The existing dependency is bcrypt and the hasher still returns a String.
This value object accepts the **structural bcrypt format only**: exactly 60
ASCII characters, revision `$2a$`, `$2b$`, or `$2y$`, two-digit cost `04`–`31`,
then `$` and 53 characters from bcrypt's `./A-Za-z0-9` alphabet.
Reject null, blanks, plaintext, unsupported algorithms/revisions, invalid costs,
wrong lengths, whitespace, and bad characters. Do not trim or normalize.
Canonical base64 padding bits and a matching raw password are not checked here.

The allowed encoded cost range is not the generation policy: the planned
configured cost remains 12. Acceptance of an encoding does not authorize
performing an expensive verification. Implement hashing, verification, input
limits, and work-factor policy separately with the library in step 8.
Argon2id/PBKDF2 would need a deliberate contract change, not silent acceptance.

`value()` returns the exact sensitive encoding for explicit future verification
or JDBC binding. **Never print or log it.** `toString()` always returns
`PasswordHash[REDACTED]`, including during concatenation and collection rendering.
Constructor errors use a constant message without the input or a parser cause.
Equality compares exact encodings; it is not constant-time password verification.
This is not automatic redaction of arbitrary strings, reflection, debugger
fields, or serialization. Future Customer rendering must also omit credentials.

#### 3. Run locally on Java 27

From the project root in IntelliJ's terminal:

```sh
mvn -version
mvn "-Dtest=PasswordHashTest" test
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest" test
```

Confirm Maven reports **Java 27**. Expect **12 tests** for PasswordHash alone
and **69 tests** combined, with **0 failures, 0 errors, 0 skipped**.
The tests cover null/malformed/valid encodings, every allowed cost, supported
revisions, exact equality/hash code, safe rendering, and secret-free errors.
No database, credentials, or environment variables are needed. Keep the root
working directory because enum tests read the SQL file as text.

**Verification status:** you reported the TransactionReference tests passed
locally on Java 27. The new 69-test focused suite passed here on Java 21.0.9
with Maven 3.8.6 and the invocation-only `-Dmaven.compiler.release=21` override.
The POM still targets Java 27; no dependencies were changed. The new
PasswordHash tests have now been confirmed passing locally on Java 27.

### Audit outcome walkthrough

Both TransactionReference and PasswordHash tests are confirmed passing locally
on Java 27. This increment implements section 31's closed financial outcome
value type, not audit persistence.

#### 1. Copy these files in IntelliJ

Open the Maven root containing `pom.xml`. Under `src/main/java`, right-click
`com.example.banking.model`, choose **New → Java Class → Enum**, and name it
`AuditOutcome`. Copy the included source. Under `src/test/java`, create the test
class in `com.example.banking.unit`, then copy the included test source.

| File | What to do |
|---|---|
| `src/main/java/com/example/banking/model/AuditOutcome.java` | Add exactly `SUCCESS`, `REJECTED`, `FAILED`, and `ROLLED_BACK`. No mutable fields or custom parser are needed. |
| `src/test/java/com/example/banking/unit/AuditOutcomeTest.java` | Add eight tests for supported names, round trips, invalid names, null, whitespace, case/Unicode lookalikes, SQL width/nullability, and persisted-name agreement. |
| `src/main/java/com/example/banking/model/TransactionAudit.java` | At this earlier enum-only step, leave it unchanged. After confirming the enum tests, use the financial audit record walkthrough below. |
| `sql/mysql/schema.sql` | Read the `transaction_audit` CHECK as text only. Do not execute it or change the existing database. |
| `README.md` | Keep this walkthrough and separate workspace/local verification. |
| `IMPLEMENTATION_PLAN.md` | Keep step 5's audit-outcome checklist and its confirmed local result. |
| `docs/decisions.md` | Keep the enum choice, exact-name contract, and scope limits. |

Keep the Java 27 target, POM, existing tests, SQL, and DAO/service placeholders
unchanged. No dependencies, credentials, or database are needed for these tests.

#### 2. Use exact names, not ordinals

```java
AuditOutcome outcome = AuditOutcome.valueOf("ROLLED_BACK");
String storedName = outcome.name(); // future JDBC binding, not a write here
```

`valueOf` rejects unknown or altered names with `IllegalArgumentException`,
including `FAILURE`, `PENDING`, lowercase names, and surrounding whitespace.
Null raises `NullPointerException`, as with the other enums. There is no
fallback to `SUCCESS`, trimming, or case conversion. Enum constants provide
immutable identity and value equality without a wrapper or extra state.
Do not persist `ordinal()`; declaration order is not a storage contract.

Login-only outcomes (`FAILURE`, `LOCKED`, `TIMEOUT`, `LOGOUT`) and ledger-only
statuses (`PENDING`, `REVERSED`) are not financial outcomes. Do not merge
those contracts. The enum alone does not validate an audit record or enforce
save-time behavior in the still-unimplemented DAO/service.

#### 3. Verify locally on Java 27

In IntelliJ's terminal, from the project root:

```sh
mvn -version
mvn "-Dtest=AuditOutcomeTest" test
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest" test
```

Confirm Maven uses Java 27. Expect **8 tests** for the new class and **77 tests**
for the combined run, with no failures, errors, or skips. For IntelliJ's Run
arrow, set the working directory to the root containing `sql/mysql/schema.sql`.
The tests read that file; they never execute its SQL.

**Workspace verification:** the original focused 65-test command (without
PasswordHashTest) passed on Java 21.0.9/Maven 3.8.6 with
`-Dmaven.compiler.release=21` for that invocation only. After syncing the
main project's PasswordHash changes, the expanded combined run passed all
77 tests with the same Java 21 toolchain and invocation-only override.
The POM remains on Java 27; workspace Java 21 results are not Java 27 verification.
**Local verification:** TransactionReference, PasswordHash, and AuditOutcome
tests were confirmed passing on Java 27 by you. The exact expanded 77-test local
run has not separately been reported. No live database constraints, audit wiring,
or transaction workflows were tested, and no SQL was executed.

### Financial audit record walkthrough

The local AuditOutcome Java 27 confirmation is received. This increment replaces
only `TransactionAudit` among the model placeholders; it does not implement
append-only JDBC writes, login auditing, services, or financial workflows.

#### 1. Copy the model and tests in IntelliJ

| File | What to do |
|---|---|
| `src/main/java/com/example/banking/model/TransactionAudit.java` | Replace the placeholder with the included final class. |
| `src/test/java/com/example/banking/unit/TransactionAuditTest.java` | Create a Java test class in `com.example.banking.unit` and copy the 20 included tests. |
| `src/main/java/com/example/banking/model/AuditOutcome.java` | Keep the confirmed four-outcome enum unchanged. |
| `src/main/java/com/example/banking/model/TransactionReference.java` | Reuse the validated reference type unchanged. |
| `sql/mysql/schema.sql` | Inspect the `transaction_audit` definition as text only. Do not execute SQL or recreate the existing database. |
| `README.md`, `IMPLEMENTATION_PLAN.md`, `docs/decisions.md` | Keep the construction rules and local/workspace results together. |

#### 2. Use the approved construction rules

| Constructor argument | Rule |
|---|---|
| `Long auditId` | Null before insertion; positive when present. `auditId()` returns `OptionalLong`. |
| `long customerId` | Positive authenticated customer ID; existence/authorization are future service/database checks. |
| `TransactionReference referenceNumber` | Required for `SUCCESS`; optional for `REJECTED`, `FAILED`, and `ROLLED_BACK`. Getter returns `Optional<TransactionReference>`. |
| `TransactionType eventType` | Required: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, or `LOAN_REPAYMENT`. Future lifecycle event types require their own explicit contract. |
| `AuditOutcome outcome` | Required; no default to success. |
| `String failureCode` | Null on success. Optional otherwise; if present, 1–40 ASCII characters matching `[A-Z][A-Z0-9_]*`. Getter returns `Optional<String>`. |
| `Instant createdAt` | Required caller-supplied timestamp; construction never reads the clock. |

```java
TransactionAudit rejected = new TransactionAudit(
        null, 42L, null, TransactionType.TRANSFER,
        AuditOutcome.REJECTED, "INSUFFICIENT_FUNDS",
        Instant.parse("2026-01-02T03:04:05Z"));
```

Import the model types and `java.time.Instant` when trying this example.
No input is trimmed or normalized. Invalid construction throws
`IllegalArgumentException` with a fixed message and no untrusted input/cause.
Failure-code syntax is not a secret detector: future callers must supply trusted,
application-defined constants such as `NOT_OWNER`, never raw SQL, exception
messages, credentials, or customer input—even if that input matches the syntax.
`toString()` includes only the closed event and outcome names, not IDs, references,
failure codes, or timestamps. Explicit getters are for authorized use and future
binding, not unrestricted logging.

All instance fields are private/final and contain immutable types. There are no
setters or ID-assignment methods; a future DAO must construct a new record when
loading an assigned ID. This entity does not add value equality or pretend that
two unsaved records share an identity. In-memory immutability is not database
append-only enforcement. SQL allows a null reference and failure code; the
success-specific restrictions above are the approved domain rules, not new DDL.
Timestamp SQL range/precision/time-zone mapping belongs to future JDBC work.

#### 3. Run locally on Java 27

From IntelliJ's terminal at the Maven root:

```sh
mvn -version
mvn "-Dtest=TransactionAuditTest" test
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest,TransactionAuditTest" test
```

Confirm Maven reports **Java 27**. Expect **20 tests** for TransactionAudit and
**97 tests** for the combined run, with no failures, errors, or skips.
For IntelliJ's Run arrow, set the working directory to the Maven root so the
schema-text test can find `sql/mysql/schema.sql`. No database credentials are
needed; no SQL is executed. Tests cover every event/outcome, identity boundaries,
required fields, optional references/codes, exact code syntax and lengths,
safe exceptions/rendering, final state, and reference DDL agreement.

**Workspace verification:** all 97 focused tests passed on Java 21.0.9 with
Maven 3.8.6 and `-Dmaven.compiler.release=21` on that invocation only.
The POM/dependencies remain unchanged and target Java 27.
**Local verification:** you confirmed AuditOutcome passed on Java 27; the new
TransactionAudit is now verified in the successful combined 105-test local run.
No database, persistence, rollback-survival, or append-only database enforcement
was tested. Keep the existing database unchanged.

### Login outcome walkthrough: continue step 5

**Progress update:** your supplied local Java 27 output confirms 105 tests
passed, including TransactionAuditTest and LoginOutcomeTest. The earlier
pending totals below describe that increment's original checkpoint.

You confirmed the financial `AuditOutcome` tests pass locally on Java 27.
This increment adds only the separate authentication/session outcome type.
It does not implement a login audit record, saving, login, lockout, or sessions.

#### 1. Add these two files in IntelliJ

Under `src/main/java`, right-click `com.example.banking.model`, choose
**New → Java Class → Enum**, and name it `LoginOutcome`. Under `src/test/java`,
create `LoginOutcomeTest` as a Java class in `com.example.banking.unit`.
Copy the included files into these exact paths:

| File | What to add |
|---|---|
| `src/main/java/com/example/banking/model/LoginOutcome.java` | Exactly `SUCCESS`, `FAILURE`, `LOCKED`, `TIMEOUT`, `LOGOUT`. |
| `src/test/java/com/example/banking/unit/LoginOutcomeTest.java` | Eight deterministic tests for names, round trips, invalid/null/blank/case/Unicode input, column width/nullability, and the login CHECK. |

Keep `AuditOutcome.java`, `LoginAudit.java`, SQL, POM, existing tests, and
DAO/service/session placeholders unchanged. No new dependency is needed.
`README.md`, `IMPLEMENTATION_PLAN.md`, and `docs/decisions.md` describe this
same increment.

#### 2. Keep login and financial outcomes separate

Use `LoginOutcome.valueOf("FAILURE")` for an exact lookup and `name()` for
future SQL binding, never ordinals. Unknown names raise
`IllegalArgumentException`; null raises the standard enum
`NullPointerException`. Do not trim, change case, accept Unicode lookalikes,
or default invalid input to `SUCCESS`.

`FAILURE` is a login outcome; financial `FAILED`, `REJECTED`, and
`ROLLED_BACK` are not. Ledger-only `PENDING`/`REVERSED` and
`PASSWORD_CHANGED` are also rejected. The existing login CHECK has no
password-change outcome; that event's storage remains a separate decision.

The tests read only the `login_audit` definition and named `chk_audit_outcome`
CHECK from `sql/mysql/schema.sql`. They compare exact names and confirm they
fit `VARCHAR(20) NOT NULL`. They do not execute SQL or prove that your local
database enforces the checked-in schema. The enum rejects bad names before
future binding, but the placeholder `LoginAudit` and DAO do not yet validate
or save records.

#### 3. Verify locally on Java 27

From IntelliJ's terminal at the Maven root:

```sh
mvn -version
mvn "-Dtest=LoginOutcomeTest" test
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest,LoginOutcomeTest" test
```

Confirm Maven reports **Java 27**. Expect **8 tests** for the new class and
**85 tests** for the combined run, with no failures, errors, or skips.
For IntelliJ's Run arrow, use JDK 27 and set the working directory to the
project root containing `sql/mysql/schema.sql`. No database settings are needed.

**Workspace verification:** all 85 focused tests, including the eight new
login tests, passed on Java 21.0.9/Maven 3.8.6 with
`-Dmaven.compiler.release=21` for that command only. The POM still targets
Java 27; this is not Java 27 verification.
**Local verification:** the preceding AuditOutcome tests are confirmed
passing on Java 27. The new LoginOutcome tests are now confirmed in the combined
105-test local run, with no failures, errors, or skips. No SQL was
executed, no database was changed, and no authentication/audit workflow was wired.

After incorporating the financial-record increment, a workspace run including
both `TransactionAuditTest` and `LoginOutcomeTest` passed **105 tests**, with
zero failures, errors, or skips on Java 21.0.9/Maven 3.8.6 using the same
command-only release override. To check both increments locally on Java 27:

```sh
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest,TransactionAuditTest,LoginOutcomeTest" test
```

The separate 85-test and 97-test commands above each omit the other increment.
The user subsequently shared the combined local result: **105 tests passed**,
with no failures, errors, or skipped tests, and **BUILD SUCCESS**.
This focused checkpoint is complete; it is not a full application test run.

### LoginAudit walkthrough: build one immutable event, step by step

**Start here:** you confirmed `LoginOutcomeTest` passes locally on Java 27.
Main-project updates also record LoginAuditTest passing all 20 tests locally.
The workspace contains completed versions of the two files below for comparison;
compare them with your IntelliJ files rather than overwriting confirmed work.
Use these steps to understand each part; do not start another feature here.

#### 1. Find the existing model file

In IntelliJ's Project panel, expand `src/main/java` →
`com.example.banking.model` and open `LoginAudit.java`.
It currently contains a placeholder in the original scaffold. Replace that
placeholder using the workspace version at the same path as your reference.
Do not create a second class with the same name.

This class represents **one event**, not the login process. It does not ask
whether a username exists or whether a password is correct.

#### 2. Add the imports and immutable fields

Keep the package declaration. Add `java.time.Instant` and
`java.util.OptionalLong`, `java.util.Optional`, and `java.util.regex.Pattern`
imports. Keep the class `public final`, following
`TransactionAudit` rather than using Java's automatically rendered `record`.
Inside it, add these fields:

```java
private final Long auditId;
private final Long customerId;
private final String usernameAttempted;
private final LoginOutcome outcome;
private final String failureReason;
private final Instant createdAt;
```

`private` prevents callers from changing fields directly; `final` assigns each
field once in the constructor. All six field types are immutable.
`Long`, unlike primitive `long`, can be null: the audit has no generated ID
before saving, and an unknown username has no customer ID. Never substitute
zero or another customer's ID for an unknown customer.

#### 3. Add the constructor and validation

Use this signature:

```java
public LoginAudit(Long auditId, Long customerId, String usernameAttempted,
                  LoginOutcome outcome, String failureReason, Instant createdAt)
```

Add the checks from the reference file **before** assigning fields:

1. If an audit ID is supplied, it must be positive.
2. If a customer ID is supplied, it must be positive. Null means unknown;
   zero and negative IDs are errors, not substitutes for null.
3. The attempted username must be non-null and at most 80 Unicode
   code points, matching `username_attempted VARCHAR(80)` in the schema.
   `codePointCount(0, usernameAttempted.length())` counts a supplementary
   Unicode character once, unlike `String.length()`.
4. Outcome and event timestamp must be supplied. Do not default to SUCCESS
   or call `Instant.now()` inside the constructor.
5. An optional failure reason is permitted only on FAILURE or LOCKED, and must
   match `[A-Z][A-Z0-9_]{0,39}`. Add the private static final Pattern from the
   reference class; the code must be an application-defined constant, not an
   exception message. Pattern matching cannot establish whether a caller used
   a trusted constant.
6. Assign each accepted argument to its corresponding `this` field.

Use `IllegalArgumentException` with fixed messages, never the rejected input.
Do not trim, lowercase, truncate, or apply registration username rules.
Empty, blank, or invalid registration names can still be useful failed-attempt
evidence. Keep them exactly; input rejection belongs to the producer.
Oversized input is rejected explicitly here; retaining that rejected evidence
requires the separate future input/audit integration, not silent truncation.
The model does not infer whether an outcome requires a known customer; that
belongs to the eventual authentication/session producer.

#### 4. Add read-only accessors and safe rendering

Copy the six accessor methods from the reference file. `auditId()` and
`customerId()` return `OptionalLong.empty()` for null, or
`OptionalLong.of(value)` for a supplied ID. The other accessors return the
stored String, enum, and Instant directly, because those types are immutable;
`failureReason()` wraps the nullable reason in `Optional<String>`.
Do not add setters, update methods, or delete methods.

Override `toString()` to return only:

```java
return "LoginAudit[outcome=" + outcome + "]";
```

Attempted usernames are untrusted and may contain private information or line
breaks. Keep them, customer IDs, audit IDs, and timestamps out of automatic
rendering. The explicit username accessor is for eventual persistence, not
permission to log that input.

For an unknown user's failed attempt, construction looks like this:

```java
LoginAudit audit = new LoginAudit(
        null, null, "unknown-user", LoginOutcome.FAILURE, null,
        Instant.parse("2026-01-02T03:04:05.123456Z"));
```

This creates an object in memory only. It neither looks up the user nor saves
a row. Optional IP address and device information remain
outside this model. Database timestamp range/precision and append-only
permissions also remain separate persistence concerns.

#### 5. Add the test class

Under `src/test/java`, right-click `com.example.banking.unit` →
**New → Java Class**, enter `LoginAuditTest`, and use the workspace file
`src/test/java/com/example/banking/unit/LoginAuditTest.java` as the reference.
Do not put tests under `src/main/java`.

Read each test as you add it:

- Exact-field construction and unknown usernames check that no identity is invented.
- Every outcome can be represented without a reason; the model does no lookup.
- ID boundary tests accept positive IDs and reject zero/negative IDs.
- Null outcome and timestamp tests prevent invented defaults.
- Username tests cover null rejection, blank preservation, 80/81-character boundaries, Unicode,
  and exact evidence preservation without registration validation.
- Error-message and rendering tests keep attempted input out of diagnostics.
- Reason tests accept only controlled codes on FAILURE and LOCKED.
- Final-field/API and accessor tests show that evidence cannot be changed;
  assigning a saved ID requires constructing another object.
- The last test reads the schema as text to check columns; it executes no SQL.

There are 20 test methods. They use a fixed `Instant`, no database configuration,
no credentials, and no authentication/session implementation.

#### 6. Run this increment locally before continuing

From IntelliJ's terminal at the folder containing `pom.xml`:

```sh
mvn -version
mvn "-Dtest=LoginAuditTest" test
```

Verify Maven reports Java 27, then expect **20 tests**, with no failures,
errors, or skips. Alternatively, use the test class's green Run arrow with
JDK 27 and the project root as its working directory; the schema-text test
needs `sql/mysql/schema.sql` available relative to that directory.

Then check the completed value/audit increments together:

```sh
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest,TransactionAuditTest,LoginOutcomeTest,LoginAuditTest" test
```

Expect **125 tests** without failures, errors, or skips.
If compilation fails, check the package, imports, constructor signature, and
placement of braces before changing behavior. If a test fails, use its method
name to identify the rule; share that failure rather than moving ahead.

**Workspace verification after reconciliation:** all 212 unit tests passed
on Java 21.0.9/Maven 3.8.6 using `mvn -Dmaven.compiler.release=21 test`,
including the preserved 20 LoginAudit tests and the existing writer/capture
tests. This is not a Java 27 or live MySQL check.

**Verification history:** the initial 121-test workspace result covered a superseded
draft, not this six-field model. Main-project documentation records the
20-test model passing locally on Java 27; the combined 125-test local run
remains pending. The POM still targets Java 27. Model tests do not prove
MySQL behavior. No SQL, POM, DAO, authentication, or session code was changed
by this model/teaching increment; preserve the separate writer work already
present in the main project.

### Identifier walkthrough: continue step 5

The prerequisite is met: you confirmed all 19 local `MoneyTest` tests pass.
Copy these four files into the same paths in your IntelliJ Maven project:

| File | What to copy and why |
|---|---|
| `src/main/java/com/example/banking/model/AccountNumber.java` | Replace the placeholder. The final class holds an immutable String, validates the exact format and Luhn checksum, and supplies value equality/hash code. |
| `src/main/java/com/example/banking/util/ReferenceGenerator.java` | Replace the placeholder. It generates account-number and transaction-reference candidates using `SecureRandom`; its clock and random source can be supplied for deterministic tests. |
| `src/test/java/com/example/banking/unit/AccountNumberTest.java` | Add the eight tests for known checksums, invalid input, single-digit errors, equality, and safe rendering. |
| `src/test/java/com/example/banking/unit/ReferenceGeneratorTest.java` | Add the eight tests for random bounds, UTC dates, hexadecimal encoding, SQL lengths, dependencies, and possible collisions. |

Keep all existing files and tests. Run this in IntelliJ's terminal with Java 27:

```shell
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest" test
```

Expect **35 tests**, with no database or environment variables required. The
generator tests use the project's existing Mockito setup. You have now shared
the successful result: 35 tests, zero failures/errors/skips, and BUILD SUCCESS.
Keep these files and tests; continue with the enum walkthrough below.

Account numbers contain 24 ASCII digits: 23 randomly generated digits, with a
nonzero first digit, followed by one Luhn check digit. They fit
`accounts.account_number VARCHAR(34)`. They are synthetic, not real IBANs.
The checksum catches common typing mistakes, not unauthorized access or every
possible transposition. Construction rejects spaces, signs, separators, Unicode
digits, incorrect lengths, leading zeroes, and bad checksums without silently
normalizing them.

```java
ReferenceGenerator generator = new ReferenceGenerator();
AccountNumber accountNumber = generator.generateAccountNumber();
String storedNumber = accountNumber.value(); // Exact value for future JDBC binding.
String reference = generator.generateTransactionReference();
```

`new AccountNumber(storedNumber)` validates a loaded/input value.
`AccountNumber.fromPayload(...)` appends a check digit to a valid 23-digit
payload; it does not reserve an account. `toString()` masks everything except
the last four digits. Do not use it for persistence or full CLI display.
Explicitly mask `value()` before logging it; validation does not prove ownership
or that the account exists.

References are exactly 40 characters: `TX-YYYYMMDD-` plus 28 uppercase
hexadecimal characters from 14 secure random bytes (112 random bits). The date
is UTC regardless of the computer's timezone; it is not a substitute for the
ledger timestamp. This fits both transaction and audit `reference_number`
columns. No customer details, account number, or counter is encoded.

Generate a reference **once per logical operation** and carry it through later
bounded retries and success/failure audit records. Never regenerate it merely
because a commit result is uncertain. MySQL's existing `UNIQUE` constraints
on account numbers and ledger references remain the final collision guard.
Generators deliberately do not query MySQL or claim to guarantee uniqueness.
Later persistence work must handle duplicate-key conflicts and reconcile
uncertain commits; a precheck alone cannot prevent races. The audit reference
is intentionally not unique, since several audit attempts can share a reference.

No SQL, database objects, account-opening service, transaction workflow, or retry
mechanism was added in the identifier increment. The enum contract checks are
covered above; remaining value types and account behavior are separate steps
in the plan.

### Login audit record walkthrough: continue step 5

The next increment implements the in-memory record, not database saving.
Copy these files to the same paths in your local IntelliJ project:

| File | Purpose |
|---|---|
| `src/main/java/com/example/banking/model/LoginAudit.java` | Immutable login/session audit record with safe validation and rendering |
| `src/test/java/com/example/banking/unit/LoginAuditTest.java` | 20 deterministic construction, boundary, immutability, and schema-text tests |

The constructor takes `auditId`, `customerId`, `usernameAttempted`, `outcome`,
`failureReason`, and `createdAt`, in that order. Audit ID is null until saved;
customer ID may be null for unknown users. Present IDs must be positive.
Supply the timestamp explicitly. Use LoginOutcome, not financial AuditOutcome.

Attempted usernames are retained exactly, including empty or invalid login
input, with an 80-Unicode-code-point maximum. This is audit capture, not
registration validation. Overlong input is rejected, not silently truncated.
For oversized failures, use the explicit marker policy described below.
Do not pass passwords, hashes, or tokens as usernames or reasons.

Failure reasons are optional on FAILURE and LOCKED and forbidden on SUCCESS,
TIMEOUT, and LOGOUT. When present, they are application-defined codes matching
`[A-Z][A-Z0-9_]{0,39}`, not free text or exception messages. This intentionally
uses less than the schema's 255-character capacity. Syntax alone cannot prove a
value is a trusted constant. The default string representation includes only
the outcome; accessors are not a promise of safe diagnostic logging.
Optional IP/device fields remain absent from this CLI increment; no metadata
is invented, and no SQL is changed.

From the Maven root in IntelliJ:

```sh
mvn -version
mvn "-Dtest=LoginAuditTest" test
mvn "-Dtest=MoneyTest,AccountNumberTest,ReferenceGeneratorTest,EnumContractTest,TransactionReferenceTest,PasswordHashTest,AuditOutcomeTest,TransactionAuditTest,LoginOutcomeTest,LoginAuditTest" test
```

Confirm Maven uses Java 27. Expect **20** tests in the focused run and **125**
in the combined run, with no failures, errors, or skips. Keep the working
directory at the project root for the schema-text test.

**Workspace verification:** all 125 tests passed on Java 21.0.7/Maven 3.9.9
with the invocation-only `-Dmaven.compiler.release=21` override. The POM still
targets Java 27; the new local Java 27 results are pending.
These are pure model tests, not mocked JDBC or real persistence tests.
No database connection, SQL execution, JDBC writer, authentication/session
integration, or database-level append-only enforcement was added.
Confirm the new local Java 27 tests before beginning the independent audit
writer. Later write-based persistence tests need an isolated database and
separate approval; leave the existing database unchanged.

### Oversized login usernames: explicit evidence policy

| File | Purpose |
|---|---|
| `src/main/java/com/example/banking/security/LoginAuditCapture.java` | Explicit capture of a rejected oversized username without retaining its contents |
| `src/test/java/com/example/banking/unit/LoginAuditCaptureTest.java` | Six tests covering ASCII, supplementary/mixed boundaries, evidence, and safe rendering |

Keep `LoginAudit` strict: direct construction still rejects usernames above
80 Unicode code points. Do not truncate, normalize, or apply registration
username rules to audit capture. At or below 80 code points, preserve the
attempt exactly, even if blank or invalid for registration.

For more than 80 code points, the future authentication caller must reject the
attempt before account lookup or password verification and explicitly call:

```java
LoginAudit audit = LoginAuditCapture.failedOversizedUsername(attemptedUsername, attemptTime);
// After finishing any primary transaction, append through LoginAuditDAO.
```

The returned unsaved record has no customer ID, outcome `FAILURE`, the fixed
username marker `[OVERSIZED_USERNAME]`, reason `USERNAME_TOO_LONG`, and the
supplied attempt time. Reserve this reason for this capture path. The marker
is **not** the supplied username: interpret the reason with it. Someone entering
the literal marker as an ordinary username must not be classified as oversized.
The factory rejects null and inputs within the limit instead of replacing them.
Count Unicode code points, not Java UTF-16 `length()`; 80 supplementary characters
are within the limit even though they occupy 160 Java characters.

This deliberately retains the event, reason, and time, not the input's text,
prefix, suffix, length, or digest. Different oversized inputs are intentionally
indistinguishable; no account identity or input correlation can be inferred.
Avoid a digest because it can fingerprint a credential accidentally typed into
the username field. Never pass a password, hash, token, or exception message to
this API. No untrusted input is copied into the marker or reason.

Return the usual generic login denial, not a registered-account hint. Never
log the raw attempt or concatenate it into an exception. The record's default
rendering remains `LoginAudit[outcome=FAILURE]`; accessors on ordinary records
can still contain untrusted text and are not safe diagnostic arguments.
Creating this record does not save it. Independent persistence is still
required, and an uncertain save must not trigger blind retries.

AI assistance added this policy and its six focused tests. All **193 unit
tests passed**, with no failures, errors, or skips, on workspace Java 21.0.9
and Maven 3.8.6 using `mvn -Dmaven.compiler.release=21 test`. The POM still
targets Java 27; this is not a verified local Java 27 result. For the focused
local run, use `mvn "-Dtest=LoginAuditTest,LoginAuditCaptureTest" test`
(26 tests). No authentication integration, registration changes, SQL changes,
or database operations were made. Database durability remains unverified here.

### Login audit JDBC writer: independent saves

Your supplied local output confirms **LoginAuditTest passed all 20 tests with
Java 27 compilation**. The model prerequisite is satisfied; the earlier pending
model confirmation above is historical.

Copy these files into the matching local packages:

| File | Purpose |
|---|---|
| `src/main/java/com/example/banking/dao/LoginAuditDAO.java` | Append-only `append(LoginAudit)` contract |
| `src/main/java/com/example/banking/dao/jdbc/JdbcLoginAuditDAO.java` | Prepared insert in an independently owned short transaction |
| `src/test/java/com/example/banking/unit/JdbcLoginAuditDAOTest.java` | 18 Mockito tests for bindings, transaction boundaries, and cleanup |

Construct the writer with a ConnectionFactory that opens a fresh connection.
There is deliberately no caller-connection argument: never supply or reuse an
authentication transaction. Once a primary operation has committed or rolled
back, append its audit separately. This increment does not wire that workflow.
Business DAOs still must not commit caller-owned transactions; this audit
writer instead owns its connection and transaction from start to finish.

The insert leaves the generated audit ID and optional IP/device fields to
the database. It binds customer ID as BIGINT or typed SQL NULL, preserves the
attempted username, binds LoginOutcome using `name()`, binds the controlled
reason or VARCHAR NULL, and supplies the record's timestamp with a UTC JDBC
calendar. Actual MySQL timestamp/session-time-zone behavior still needs a
separately approved isolated test. No clock reading or credential data is added.
Pass only unsaved records; append does not mutate the record or return its ID.

Exactly one affected row is required. The statement closes before commit;
SQL failures during preparation/binding/execution/statement close trigger a
rollback attempt, and both resources are closed. A commit error also triggers
a best-effort rollback, but that does **not** prove a commit did not happen.
A connection-close error after commit is reported without rolling back or
repeating the insert. All driver exception details, including suppressed
cleanup errors, are discarded at this boundary in favor of a safe error.
No exception is copied into a stored reason, and nothing is logged here.
**Do not automatically retry a failed append:** commit/cleanup failures may
leave an already saved row. Reconciliation/idempotency is not implemented.

Run locally on Java 27:

```sh
mvn -version
mvn "-Dtest=JdbcLoginAuditDAOTest" test
mvn test
```

Expect **18 tests** in the focused writer run. The current checkout's full
unit suite has **187 tests**, all passed on Java 21.0.9/Maven 3.9.9 using only
the invocation override `-Dmaven.compiler.release=21`. The POM still targets
Java 27; local Java 27 writer results are pending. Local totals may differ
if your checkout contains other tests.

Mockito verifies JDBC calls, not actual SQL acceptance, foreign keys, durable
commit, rollback survival, or database privileges. No schema SQL was executed,
no database was changed, and no integration profile was run. Real persistence
testing requires an isolated test database and separate approval.

### Real MySQL login audit checks

For separately approved disposable-server tests proving that unsafe test-account
grants block fixture creation and cleanup without losing existing evidence, see
[live grant-refusal verification](docs/login-audit-grant-verification.md).
That suite is selected independently of the DAO timestamp checks below.

The user separately approved these write-based checks. They exercise
`JdbcLoginAuditDAO` without connecting authentication or changing the application
database. Add these files to the same packages in your local project:

| File | Purpose |
|---|---|
| `src/test/java/com/example/banking/integration/LoginAuditTestDatabase.java` | Fail-closed connection, schema ownership, setup, and cleanup guards |
| `src/test/java/com/example/banking/integration/JdbcLoginAuditDAOIT.java` | Real commits, independent rollback, NULL/FK checks, and timestamp round trips |
| `src/test/java/com/example/banking/unit/LoginAuditDatabaseGuardTest.java` | Four configuration-safety tests without database access |

Use a **dedicated, empty `banking_test` schema on an isolated MySQL 8 server**.
Do not point these tests at an application server, reuse application credentials,
or run the sample-data script. A test administrator must create the empty schema
and a password-protected `banking_test_runner` account with privileges only on
`banking_test.*`, without grant option or roles/global privileges. The tests do
not create a server, database, or user. Run exclusively; concurrent test suites
must not share this schema.

Set private test configuration in your local terminal or unshared IntelliJ Maven
configuration. Never commit passwords or put them in command-line arguments:

| Setting | Required value |
|---|---|
| `TEST_DB_URL` | `jdbc:mysql://127.0.0.1:3307/banking_test` with your isolated server's explicit port; `localhost` also accepted |
| `TEST_DB_USERNAME` | `banking_test_runner` |
| `TEST_DB_PASSWORD` | That test-only account's private password |
| `LOGIN_AUDIT_IT_APPROVED` | `CREATE_LOGIN_AUDIT_FIXTURES_IN_BANKING_TEST` after approving fixture creation/removal |

Remote test servers need a local tunnel. URL options, alternate catalogs/hosts,
embedded credentials, and failover URLs are rejected before connecting. There
is no `DB_*` fallback. On every connection, the harness checks the actual
database, authenticated principal, MySQL 8 version, and grants before any writes.
It refuses a nonempty schema without dropping or reusing existing objects.
It extracts only `customers` and `login_audit` DDL from `sql/mysql/schema.sql`;
it does not run the full schema, triggers, sample data, or migrations.
Cleanup drops only tables this invocation successfully created, audit first.
If the process is forcibly stopped, inspect leftover test objects manually;
the next run will refuse them rather than deleting them automatically.

With Java 27, run from the Maven root:

```sh
mvn test
mvn verify -Pintegration "-Dit.test=JdbcLoginAuditDAOIT"
```

The explicit `-Dit.test` selection matters: the older
`ConnectionFactoryReadOnlyIT` reads application configuration and is not part of
this isolated write-test run. `mvn test` alone never runs `*IT` classes.
Missing approval, missing settings, unreachable MySQL, unsafe grants/targets,
and nonempty schemas fail the integration run; they are not skipped coverage.
Inspect `target/failsafe-reports` for the actual executed results.

The seven integration cases check:

- A failed-login audit commits while a separate primary connection has an
  uncommitted customer fixture; fresh observers see the audit before and after
  primary rollback, while the customer fixture disappears.
- Unknown customers and absent reasons persist as SQL NULL; attempted input
  containing SQL-like text is stored unchanged.
- A committed customer's FK succeeds, a nonexistent customer's FK fails, and
  the earlier committed row remains.
- A second harness refuses existing tables without deleting their evidence.
- Three timestamp cases use UTC writer sessions and reader sessions `+00:00`,
  `-05:00`, and `+05:30`, with matching JDBC calendars. Both server epoch seconds
  and retrieved Instants must equal the supplied timestamp.

**Timestamp boundary:** the writer passes a UTC JDBC calendar. These verified
round trips require a UTC MySQL **writer session**, not merely a UTC JVM.
The original live run used test-only Connector/J UTC properties and session
`time_zone='+00:00'`. The current harness instead uses the explicit application
UTC connection path described below. A reader that changes its session zone
must use a matching calendar. Arbitrary unconfigured writer sessions are not
promised safe; ordinary business connections still do not enforce UTC.
The schema uses `TIMESTAMP` without fractional precision, so these cases claim
whole-second round trips only, not arbitrary Instant range/nanosecond fidelity.
Fixed offsets avoid dependence on installed MySQL named-time-zone tables.

**Actual workspace verification:** MySQL 8.0.42, Connector/J 26.10.0, Java
21.0.7, and Maven 3.9.9 were used on a new, disposable loopback-only server with
no application schema. All **191 unit tests** and **7 real integration cases**
passed without skips, using the invocation-only `-Dmaven.compiler.release=21`
override. A separate run without approval failed before connecting, as intended.
The test schema was empty after cleanup. The POM remains Java 27; local Java 27
and MySQL 8.4+ verification remain unconfirmed. The earlier 18 Mockito writer
tests still prove interactions/failure cleanup only. These live tests prove
database behavior on the stated setup, not crash recovery, production grants,
authentication workflow wiring, or database-level append-only enforcement.

### Login audit timestamp acceptance

The user chose to reject unsupported times at the persistence boundary, not
change the schema or silently reduce timestamp precision. `LoginAudit` still
accepts every non-null `Instant` and keeps it unchanged. `JdbcLoginAuditDAO`
accepts only whole seconds in the inclusive UTC range
**1970-01-01T00:00:01Z through 2038-01-19T03:14:07Z**. These are the portable
MySQL `TIMESTAMP(0)` bounds, even if a particular server supports a wider range.

A fractional value (including one nanosecond) or a time outside that range
throws a safe `IllegalArgumentException` **before a connection is acquired**.
No insert or save is attempted. This is different from a `PersistenceException`,
which can mean that a commit happened but was not confirmed. Neither the model
nor the DAO rounds, truncates, clamps, or replaces the supplied time.
The caller must retain the rejected record and explicitly handle the failure;
this change does not create an alternative durable evidence store.
In particular, `Instant.now()` commonly includes fractions and cannot simply
be passed to this writer without an approved caller capture policy.
Authentication remains unconnected. UTC writer-session requirements still apply.

Updated files:

| File | What changed |
| --- | --- |
| `src/main/java/com/example/banking/dao/LoginAuditDAO.java` | Documents known pre-write rejection versus uncertain persistence failure |
| `src/main/java/com/example/banking/dao/jdbc/JdbcLoginAuditDAO.java` | Checks precision and inclusive bounds before opening JDBC |
| `src/test/java/com/example/banking/unit/JdbcLoginAuditDAOTest.java` | Adds 15 database-free cases for fractions, exact bounds, adjacent seconds, nanosecond excursions, and extreme Instants |
| `src/test/java/com/example/banking/integration/JdbcLoginAuditDAOIT.java` | Adds 9 guarded live cases for exact-bound round trips and rejection without losing earlier evidence |

**Current database-free result:** `mvn -Dmaven.compiler.release=21 test` passed
all **206 tests**, including **33 mocked writer cases**, with no failures,
errors, or skips on Java 21.0.7 / Maven 3.9.9. Integration sources compiled,
but no live cases ran. The POM still targets Java 27; this is not Java 27
verification.

**Historical live status before the expanded run:** the earlier seven-case result above predates this change.
The expanded suite has **16 cases**, and its new boundary cases have **not
been run against MySQL**. Run it only after separate approval against the
isolated test database, following the existing guards:

```bash
mvn verify -Pintegration "-Dit.test=JdbcLoginAuditDAOIT"
```

That command creates fixtures and cleans them up; it is not a read-only check.

### Expanded login audit verification on the configured target

After separate explicit approval for this run, the command above passed on
**OpenJDK 27.0.1.0.3, Maven 3.9.9, MySQL 8.4.5, and Connector/J 26.10.0**.
Maven compiled the main and test sources with **release 27**, without a compiler
override. It ran **212 unit tests and all 16 `JdbcLoginAuditDAOIT` cases**:
zero failures, errors, or skips. The older Java 21 database-free and seven-case
MySQL 8.0 results remain separate historical evidence, not the basis for this
result.

The new server was disposable, bound to loopback port 3307, and had no
application schema. The harness used `banking_test_runner@localhost` with only
`SELECT, INSERT, CREATE, DROP, REFERENCES` on `banking_test.*`; no global write
privileges, roles, or grant option were assigned. Existing target, ownership,
empty-schema, and UTC-session guards remained unchanged. No application
configuration, credentials, data, full schema script, or migrations were used.

Both inclusive bounds round-tripped exactly as retrieved Instants and MySQL
epoch seconds:

| UTC Instant | Epoch seconds |
| --- | --- |
| `1970-01-01T00:00:01Z` | `1` |
| `2038-01-19T03:14:07Z` | `2147483647` |

All seven unsupported-time cases threw `IllegalArgumentException`, retained the
original model time, inserted no matching row, left total row count unchanged,
and preserved a previously committed audit row. The original rollback, foreign
key, SQL NULL, existing-table refusal, and three reader-offset cases also passed.
The exact bounds were read in UTC; the three reader offsets used the ordinary
whole-second fixture, not the bounds.

Independent schema counts were **0 tables before setup and 0 after cleanup**.
The disposable server was then shut down. This confirms the guarded DAO suite
on the stated versions, not authentication wiring, arbitrary writer time zones,
crash recovery, or additional timestamp precision.

See [the verification record](docs/login-audit-verification.md) for the complete
case breakdown. Generated logs, reports, downloaded tools, and disposable data
remain excluded from version control.
No database was contacted, no schema was changed, and no writes were authorized
or executed for this increment.
### Explicit UTC connections for login audit writes

`ConnectionFactory.getLoginAuditConnection()` opens a fresh connection and runs
`SET SESSION time_zone = '+00:00'` before returning it. `JdbcLoginAuditDAO`
always uses this path, before starting its independent transaction or inserting
evidence. A numeric offset works without MySQL named-time-zone tables. The
existing UTC JDBC calendar remains required for binding the supplied Instant.
This does not depend on the JVM, server default, or URL `connectionTimeZone`.

Ordinary `getConnection()` and `fromEnvironment()` behavior is unchanged:
business callers run no new setup SQL. No global setting, schema, or
authentication wiring was changed. Each returned connection remains
caller-owned and must be fresh, not a shared business transaction connection.

UTC setup includes closing its statement. If creation, execution, or closure
fails, the connection is closed and never returned to the DAO; no insert or
transaction starts. SQL setup failures follow the DAO's existing sanitized,
no-automatic-retry `PersistenceException` contract. The caller must still retain
unsaved evidence; no fallback storage was added. Timestamp range/precision
rejection remains unchanged and still happens before any connection acquisition.

**Database-free verification:** `mvn -Dmaven.compiler.release=21 test` passed
218 tests, zero failures/errors/skips, on Java 21.0.7 / Maven 3.9.9. Twelve new
factory/DAO tests check ordering, per-connection setup, ordinary-path isolation,
cleanup failures, and no insert/retry after failed setup. Integration sources
compiled. Java 27 remains the POM target and was not verified.

**Live verification was separately approved and passed for this change.**
`LoginAuditUtcConnectionIT` requires a disposable MySQL 8 server started with
`--default-time-zone=+05:30`, an empty `banking_test` schema, and the same
restricted test principal and private approval/settings described above.
It checks actual global and ordinary-session defaults before creating fixtures;
it never changes global settings. Five cases use the production UTC writer path
and compare server epoch seconds and UTC-calendar retrieved Instants, including
both exact supported bounds and seasonal dates. Subsequent ordinary connections
must still inherit `+05:30`. The harness no longer preconfigures writer UTC via
driver properties, so it cannot mask a missing application policy.

After approval and isolated setup only:

```bash
mvn -Dmaven.compiler.release=21 verify -Pintegration \
  "-Dit.test=LoginAuditUtcConnectionIT"
```

Use Java 27 without the release override when available. This command creates
and removes only owned test fixtures; it is not read-only. Never use the
application database, its credentials, or earlier approval for this run.

**Actual live result:** all five `LoginAuditUtcConnectionIT` cases and all
16 existing `JdbcLoginAuditDAOIT` regression cases passed without failures,
errors, or skips on a newly initialized loopback-only MySQL 8.0.42 server with
`--default-time-zone=+05:30`. Connector/J 26.10.0, Java 21.0.7, and Maven 3.9.9
were used; the verify invocation also passed all 218 unit tests. Both UTC range
limits round-tripped unchanged. Ordinary sessions and the global default
remained `+05:30`; fixture cleanup left zero tables in `banking_test`.
The disposable server was stopped. It had no application schema, and the
application database was never contacted. This is not Java 27, MySQL 8.4+,
arbitrary driver-option combinations, or arbitrary reader-offset verification.

After incorporating the newer delivery tests, their mock connection
expectations were updated to use the explicit UTC path. The final full unit run
passed **273 tests**, and both guarded live suites were rerun together:
**21 cases passed**, no failures/errors/skips. The fresh disposable server
again retained `+05:30` globally, cleanup left zero test tables, and shutdown
completed normally. These final runs used the same versions listed above.

### UTC policy verified on Java 27 and MySQL 8.4

After new, separate approval, the combined audit run passed on **OpenJDK
27.0.1.0.3, Maven 3.9.9, MySQL 8.4.5, and Connector/J 26.10.0**:

```sh
mvn clean verify -Pintegration \
  "-Dit.test=LoginAuditUtcConnectionIT,JdbcLoginAuditDAOIT"
```

Both source sets compiled with the configured **release 27**, with no release
override. All **273 unit tests and 21 integration cases** passed, with zero
failures, errors, or skips. This includes all five UTC-policy cases and all
16 DAO regression cases; it supersedes the earlier pending runtime status
for these suites only.

The fresh disposable server listened only on `127.0.0.1:3307`, started with
`--default-time-zone=+05:30`, and contained no application schema. Only the
restricted `banking_test_runner` account ran the suites. All existing test-target
guards remained unchanged. Independent checks found **zero test tables before
and after**; the global default remained `+05:30` and the server shut down
normally. No application database, application credentials, migration, or full
schema script was used.

See [the runtime verification record](docs/login-audit-verification.md#combined-utc-policy-run-on-the-configured-runtime)
for the evidence and limitations. This is workspace verification, not
confirmation that your local IntelliJ files have been updated or tested.

### Login audit delivery handoff

`AuthService` and `LoginMenu` are still placeholders. This increment adds the
delivery policy they can use later, not a working login flow.

| File | Purpose |
| --- | --- |
| `src/main/java/com/example/banking/security/LoginAuditDelivery.java` | One append attempt and an explicit result retaining the original evidence |
| `src/test/java/com/example/banking/unit/LoginAuditDeliveryTest.java` | 17 database-free delivery cases using mocked writers/connections |

In IntelliJ, add the delivery class under `security` and its test under
`unit`. Read `deliver` first: it accepts an already captured, unsaved record;
it neither reads a clock nor changes the record. Then run
`mvn "-Dtest=LoginAuditDeliveryTest" test` on the project's Java 27 setup.

Future login wiring must finish the primary authentication/session transaction
once, then call `new LoginAuditDelivery(audits).deliver(evidence)` once and
keep the returned `Result`. Inspect **delivery status**, not the authentication
outcome inside the evidence:

| Status | Meaning and caller responsibility |
| --- | --- |
| `SAVED` | The writer returned normally. Only this result confirms a save. |
| `REJECTED_NO_WRITE` | The DAO raised its documented pre-write `IllegalArgumentException`. Keep `result.evidence()` unchanged for explicit handling; do not truncate, clamp, replace its time, or retry the same unsupported value. |
| `SAVE_UNCERTAIN` | The DAO raised `PersistenceException`; the row may exist. Keep the evidence and uncertainty together for reconciliation. Do not retry automatically. |

Ordinary fractional `Instant.now(clock)` values follow the rejection path,
even when the recorded authentication outcome is `SUCCESS`. Nothing here
repeats customer lookup, password checks, counters, lockout changes, or session
creation. There is no authentication callback or retry loop. Delivery does not
roll back a completed authentication operation or turn it into an apparent
authentication failure that invites repetition.

The result holds the **same immutable object**, with its exact timestamp and
ordinary attempted username. Result rendering includes only delivery status,
not identity, time, reason, or exception details. Do not log the evidence or
send it to the CLI user. Oversized attempts still require the existing marker
capture path before lookup/password checking; pass that marker record through
the same delivery component without reconstructing the raw input.

This is an **in-memory handoff, not durable retention**. The eventual caller
must not discard a non-saved result or claim that returning it stores evidence
across process exit. No fallback file, queue, database table, or redelivery
policy has been approved here. Before production login wiring, choose protected
durable retention and an explicit unresolved-delivery response. The separate
UTC writer-session and oversized-input integration requirements still apply.

Verification in this checkout: `mvn -Dmaven.compiler.release=21 test` passed
**229 tests**, including the **17 new delivery cases**, with no failures,
errors, or skips on Java 21.0.7 / Maven 3.9.9. The POM remains Java 27; a local
Java 27 run is not claimed. Tests exercise the real delivery/JDBC writer with
mocked JDBC and a simulated completed primary operation, not real authentication
or durable database saves. No database was contacted or schema changed.
