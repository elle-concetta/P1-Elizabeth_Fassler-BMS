# Secure Banking CLI
A banking application being built with **Java 27, Maven, JDBC, and MySQL 8.4.x**.

The goal is to let customers register, sign in, manage accounts, and carry out banking transactions while learning how to handle validation, security, and database transactions correctly.

## Demo Presentation# Secure Banking CLI
A banking application being built with **Java 27, Maven, JDBC, and MySQL 8.4.x**.

The goal is to let customers register, sign in, manage accounts, and carry out banking transactions while learning how to handle validation, security, and database transactions correctly.

## Demo Presentation
[![Secure Banking CLI Presentation](https://github.com/elle-concetta/P1-Elizabeth_Fassler-BMS/blob/fd60c5aedd854a1dfd0292acbcb9493784f24840/Secure%20Banking%20CLI.png)](https://www.youtube.com/watch?v=bZWHj967qDo)

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
├── .mvn/                                 empty; no Maven wrapper installed
├── .gitignore
├── pom.xml
├── README.md
├── sql/
│   ├── mysql/schema.sql                  extended nine-table MySQL initial schema
│   └── sample-data.sql                   optional synthetic data
├── src/main/resources/
│   ├── app.properties
│   └── logback.xml
├── src/main/java/com/example/banking/
│   ├── app/                              entry point, menus, input
│   │   ├── BankingApplication.java       composition root; no schema changes
│   │   ├── MainMenu.java                 welcome, registration, and login routing
│   │   ├── CustomerMenu.java             profile, contact, password, and account routing
│   │   ├── AccountMenu.java              owned accounts and deposit confirmation
│   │   └── ConsoleInput.java             synchronized bounded input and hidden passwords
│   ├── model/                            domain types and enums
│   │   ├── Customer.java                 synchronized public profile; no password hash
│   │   ├── ContactDetails.java           editable contact fields only
│   │   ├── SecurityAuditEvent.java       password-change metadata, never credentials
│   │   ├── Account.java                  shared account invariants
│   │   ├── SavingsAccount.java           Savings account model
│   │   ├── CurrentAccount.java           Current account model
│   │   ├── DepositAccount.java           term-deposit model; workflow still pending
│   │   ├── LoanAccount.java              loan model; workflow still pending
│   │   ├── BankTransaction.java          deposit receipt and ledger values
│   │   ├── Money.java                    immutable decimal value object
│   │   ├── AccountNumber.java            immutable synthetic number with Luhn validation
│   │   ├── TransactionReference.java     immutable, exact-format correlation reference
│   │   ├── PasswordHash.java             immutable bcrypt encoding with redacted rendering
│   │   ├── AuditOutcome.java             closed financial-attempt outcomes
│   │   ├── TransactionAudit.java         immutable, validated financial-attempt record
│   │   ├── LoginOutcome.java             separate closed authentication/session outcomes
│   │   ├── LoginAudit.java               immutable minimal login evidence; no persistence
│   │   ├── AccountType.java              persisted account types
│   │   ├── AccountStatus.java            persisted statuses; excludes CREATED
│   │   ├── CustomerStatus.java           persisted customer statuses
│   │   ├── TransactionType.java          persisted movement types
│   │   ├── TransactionStatus.java        reference ledger statuses
│   │   └── policy/                       eligibility, transfer, password, lockout
│   ├── service/                          use cases and transaction boundaries
│   │   ├── AuthService.java              authentication and protected service routing
│   │   ├── CustomerService.java          profile reads and contact updates
│   │   ├── PasswordChangeService.java    password verification and replacement
│   │   ├── AccountService.java           Savings/Current opening and enquiry
│   │   └── TransactionService.java       deposits; other operations still pending
│   ├── dao/                              repository interfaces
│   │   └── jdbc/                         customer, account, ledger, and audit writers
│   ├── security/                         hashing and session contracts
│   ├── validation/                       customer, account, and amount validation
│   ├── exception/                        typed failures
│   └── util/                             configuration, connections, transaction helper
│       ├── AppConfig.java
│       ├── ConnectionFactory.java
│       ├── LoggerFactory.java
│       └── ReferenceGenerator.java        secure account/reference candidates
└── src/test/java/com/example/banking/
    ├── unit/
    │   ├── AccountAuthTest.java            authenticated account routing
    │   ├── AccountCliTest.java             account menu checks
    │   ├── AccountFixtures.java            shared synthetic account helpers
    │   ├── AccountModelTest.java           account model invariants
    │   ├── AccountServiceTest.java         mocked opening and enquiry
    │   ├── CustomerContactCliTest.java     contact form routing
    │   ├── CustomerServiceTest.java        mocked profile and contact persistence
    │   ├── DepositAuthTest.java            protected deposit routing
    │   ├── DepositCliTest.java             deposit form and receipt output
    │   ├── DepositFixtures.java            shared synthetic deposit helpers
    │   ├── DepositServiceTest.java         31 mocked deposit transaction cases
    │   ├── DepositValidationTest.java      amount and timestamp boundaries
    │   ├── JdbcAccountDAOTest.java         owner-scoped account SQL
    │   ├── JdbcDepositDAOTest.java         deposit balance and ledger SQL
    │   ├── PasswordChangeAuthTest.java     protected password-change routing
    │   ├── PasswordChangeCliTest.java      hidden-password form handling
    │   ├── PasswordChangeServiceTest.java  mocked password changes
    │   ├── SecurityAuditTest.java          security event and JDBC mapping checks
    │   ├── ConsoleInputTest.java           five executable bounded-input checks
    │   ├── CustomerProfileTest.java        three executable public-profile checks
    │   ├── AuthenticationSecurityTest.java executable bcrypt checks
    │   ├── AuthServiceTest.java            executable mocked authentication checks
    │   ├── CliPresentationTest.java        executable banner and masking checks
    │   ├── JdbcCustomerDAOTest.java        executable mocked JDBC checks
    │   ├── PhoneNumberValidationTest.java  executable supplied phone-policy checks
    │   ├── SessionManagerTest.java         executable clock-driven session checks
    │   ├── RegistrationLoginCliTest.java   restored stream-driven CLI checks
    │   ├── AppConfigTest.java              configuration tests included here
    │   ├── ConnectionFactoryTest.java      connection-opening tests included here
    │   ├── ConnectionMockTest.java         JUnit/Mockito setup test included here
    │   ├── LoggingTest.java                six logging tests passed locally
    │   ├── MoneyTest.java                  19 money tests passed locally
    │   ├── AccountNumberTest.java          eight account-number tests
    │   ├── ReferenceGeneratorTest.java     eight generator tests
    │   ├── TransactionReferenceTest.java   twelve reference validation tests
    │   ├── PasswordHashTest.java twelve    encoding, equality, and redaction tests
    │   ├── AuditOutcomeTest.java           eight outcome/SQL-text contract tests
    │   ├── TransactionAuditTest.java 20    construction, immutability, and rendering tests
    │   ├── JdbcTransactionAuditDAOTest.java   32 mocked financial-audit writer cases
    │   ├── LoginOutcomeTest.java           eight login outcome/SQL-text contract tests
    │   ├── LoginAuditTest.java             20 construction, nullability, and immutability tests
    │   ├── LoginAuditCaptureTest.java      six oversized-attempt capture tests
    │   ├── JdbcLoginAuditDAOTest.java      33 mocked login-audit writer cases
    │   ├── LoginAuditDatabaseGuardTest.java   isolated database safety checks
    │   └── EnumContractTest.java           ten persisted-name/schema checks
    ├── service/
    └── integration/
        ├── ConnectionFactoryReadOnlyIT.java     read-only factory connectivity check
        └── MySqlConnectionTest.java             local-only JDBC connection check
```

## Build and Run

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

## Required Libraries

| Library | Purpose | Current status |
|---|---|---|
| MySQL Connector/J | JDBC connections and MySQL operations | Declared in `pom.xml` |
| JUnit Jupiter | Automated tests | Local setup tests reported passing |
| Mockito Core | Mock DAOs and JDBC objects for isolated tests | Local mock-connection test reported passing |
| Mockito JUnit Jupiter | Initialize Mockito mocks in JUnit 5 | Used in the local mock-connection test |
| SLF4J API | Logging interface | Declared |
| Logback Classic | Logging implementation, configured by `logback.xml` | INFO/stderr configuration; all six logging tests passed locally |
| bcrypt | Salted adaptive password hashing and verification | Declared; implementation pending |


### Dependency Coordinates and Versions

The versions below match the local setup guidance and are now declared in the repository's POM. This does not mean the full dependency tree has been built or security-checked here. The project guidelines name the required libraries but do not pin their versions. Keep checking dependency security warnings when updating the build.

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

[![Secure Banking CLI Presentation](https://github.com/elle-concetta/P1-Elizabeth_Fassler-BMS/blob/fd60c5aedd854a1dfd0292acbcb9493784f24840/Secure%20Banking%20CLI.png)](https://www.youtube.com/watch?v=bZWHj967qDo)

## Documentation Navigation
- [Project overview and current status](#current-status)
- [Copy, build and run the final feature batch](#term-deposit-and-loan-handoff)
- [Add synthetic customers safely](#dummy-data-guide)
- [What to show and what remains](#demo-verification-and-remaining-work)
- [Step-by-step implementation plan](#implementation-plan)
- [Design decisions](#design-decisions)
