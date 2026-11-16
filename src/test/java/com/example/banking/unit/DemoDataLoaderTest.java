package com.example.banking.unit;

import com.example.banking.app.DemoDataLoader;
import com.example.banking.dao.AccountDAO;
import com.example.banking.model.*;
import com.example.banking.security.PasswordHasher;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.sql.*;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DemoDataLoaderTest {
    private ConnectionFactory factory; private Connection c; private AccountDAO accounts;
    private PasswordHasher hasher; private PreparedStatement insert, count, database;
    private ResultSet countRows, databaseRows;
    // Synthetic unit-test input, not a shared demo login or stored application credential.
    private final char[] password = "Training-Unit9!".toCharArray();
    @BeforeEach void setup() throws Exception {
        factory = mock(ConnectionFactory.class); c = mock(Connection.class); accounts = mock(AccountDAO.class);
        hasher = mock(PasswordHasher.class); insert = mock(PreparedStatement.class);
        count = mock(PreparedStatement.class); database = mock(PreparedStatement.class);
        countRows = mock(ResultSet.class); databaseRows = mock(ResultSet.class);
        var metadata = mock(DatabaseMetaData.class); var keys = mock(ResultSet.class);
        when(factory.getConnection()).thenReturn(c); when(c.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(c.createStatement()).thenReturn(mock(Statement.class));
        when(c.prepareStatement("SELECT DATABASE()")).thenReturn(database);
        when(c.prepareStatement(startsWith("SELECT COUNT(*)"))).thenReturn(count);
        // Mockito registers the constant matcher but returns a dummy int during stubbing.
        //noinspection MagicConstant
        when(c.prepareStatement(startsWith("INSERT INTO customers"), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(insert);
        when(database.executeQuery()).thenReturn(databaseRows); when(databaseRows.next()).thenReturn(true);
        when(databaseRows.getString(1)).thenReturn("banking_db");
        when(count.executeQuery()).thenReturn(countRows); when(countRows.next()).thenReturn(true);
        when(countRows.getLong(1)).thenReturn(0L);
        when(insert.executeUpdate()).thenReturn(1); when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        var customers = new AtomicLong(9);
        when(keys.getLong(1)).thenAnswer(ignored -> customers.incrementAndGet());
        var salts = new AtomicInteger();
        when(hasher.hash(password)).thenAnswer(ignored -> "MOCK_ENCODED_HASH_" + salts.incrementAndGet());
        var ids = new AtomicLong(20);
        when(accounts.insert(eq(c), anyLong(), any(), any(), any())).thenAnswer(ignored -> ids.incrementAndGet());
    }
    @AfterEach void clear() { java.util.Arrays.fill(password, '\0'); }
    @Test void explicitSeedCreatesOneHundredCustomersAndTwoHundredEmptyAccountsAtomically() throws Exception {
        var result = DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password);
        assertEquals(200, result.size());
        assertEquals(100, result.stream().map(DemoDataLoader.DemoAccount::username).distinct().count());
        assertEquals(200, result.stream().map(DemoDataLoader.DemoAccount::id).distinct().count());
        assertEquals(100, result.stream().filter(a -> a.type() == AccountType.SAVINGS).count());
        assertEquals(100, result.stream().filter(a -> a.type() == AccountType.CURRENT).count());
        verify(accounts, times(200)).insert(eq(c), anyLong(), any(), any(), any());
        var owners = ArgumentCaptor.forClass(Long.class);
        verify(accounts, times(200)).insert(eq(c), owners.capture(), any(), any(), any());
        assertEquals(100, new HashSet<>(owners.getAllValues()).size());
        var hashes = ArgumentCaptor.forClass(String.class);
        verify(insert, times(100)).setString(eq(7), hashes.capture());
        assertEquals(100, new HashSet<>(hashes.getAllValues()).size());
        assertTrue(hashes.getAllValues().stream().allMatch(hash -> hash.startsWith("MOCK_ENCODED_HASH_")));
        var order = inOrder(hasher, insert);
        order.verify(hasher, times(100)).hash(password);
        order.verify(insert, times(100)).executeUpdate();
        assertFalse(result.getFirst().toString().contains(result.getFirst().number().value()));
        verify(c).commit(); verify(c).close(); verify(c, never()).rollback();
    }
    @Test void existingSyntheticIdentityAbortsWithoutUpdatingOrInsertingAnything() throws Exception {
        when(countRows.getLong(1)).thenReturn(1L);
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertFalse(failure.uncertain()); verifyNoInteractions(insert, accounts, hasher);
        verify(c).rollback(); verify(c, never()).commit();
    }
    @Test void isolatedFixtureOrOtherDatabaseCannotBeSeededAccidentally() throws Exception {
        when(databaseRows.getString(1)).thenReturn("banking_test");
        assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        verifyNoInteractions(insert, count, accounts, hasher);
    }
    @Test void failureDuringAccountCreationRollsBackAllNewCustomersAndAccounts() throws Exception {
        when(accounts.insert(any(), anyLong(), any(), any(), any())).thenThrow(new SQLException("PRIVATE_DRIVER_DETAIL"));
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertFalse(failure.uncertain()); assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("PRIVATE_DRIVER_DETAIL"));
        verify(c).rollback(); verify(c, never()).commit();
    }
    @Test void commitFailureIsExplicitlyUncertainAndNeverRetried() throws Exception {
        doThrow(new SQLException("PRIVATE_DRIVER_DETAIL")).when(c).commit();
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertTrue(failure.uncertain()); verify(c).commit();
        verify(insert, times(100)).executeUpdate();
    }
    @Test void loaderWithoutExplicitConfirmationDoesNotAskForCredentialsOrOpenADatabase() {
        assertDoesNotThrow(() -> DemoDataLoader.main(new String[0]));
    }
    @Test void identitiesAndTenDigitPhonesStayUniqueAcrossAllOneHundredCustomers() throws Exception {
        DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password);
        var usernames = ArgumentCaptor.forClass(String.class);
        var phones = ArgumentCaptor.forClass(String.class);
        var firstNames = ArgumentCaptor.forClass(String.class);
        var lastNames = ArgumentCaptor.forClass(String.class);
        verify(insert, times(100)).setString(eq(6), usernames.capture());
        verify(insert, times(100)).setString(eq(5), phones.capture());
        verify(insert, times(100)).setString(eq(2), firstNames.capture());
        verify(insert, times(100)).setString(eq(3), lastNames.capture());
        assertEquals("training_demo_001", usernames.getAllValues().getFirst());
        assertEquals("training_demo_100", usernames.getAllValues().getLast());
        assertEquals(100, new HashSet<>(usernames.getAllValues()).size());
        assertEquals(100, new HashSet<>(phones.getAllValues()).size());
        assertEquals("2025550100", phones.getAllValues().getFirst());
        assertEquals("2025550199", phones.getAllValues().getLast());
        assertTrue(phones.getAllValues().stream().allMatch(phone -> phone.matches("[0-9]{10}")));
        var names = new HashSet<String>();
        for (int i = 0; i < 100; i++) names.add(firstNames.getAllValues().get(i) + " " + lastNames.getAllValues().get(i));
        assertEquals(100, names.size());
        verify(count).setString(1, "training_demo_001");
        verify(count).setString(100, "training_demo_100");
        verify(count).setString(101, "training_demo_001@example.invalid");
        verify(count).setString(200, "training_demo_100@example.invalid");
        verify(count, never()).setString(anyInt(), eq("training_demo_01"));
        verify(count, never()).setString(anyInt(), eq("training_demo_02"));
        verify(count, never()).setString(anyInt(), eq("training_demo_03"));
    }
    @Test void failureOnTheLastAccountRollsBackTheWholeHundredCustomerBatch() throws Exception {
        var writes = new AtomicInteger();
        when(accounts.insert(eq(c), anyLong(), any(), any(), any())).thenAnswer(ignored -> {
            int write = writes.incrementAndGet();
            if (write == 200) throw new SQLException("PRIVATE_DRIVER_DETAIL");
            return 1000L + write;
        });
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertFalse(failure.uncertain());
        verify(insert, times(100)).executeUpdate();
        verify(c).rollback(); verify(c, never()).commit();
    }
    @Test void weakPasswordsCannotReachTheDatabaseOrHasher() {
        assertThrows(com.example.banking.exception.ValidationException.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, "weak".toCharArray()));
        verifyNoInteractions(factory, hasher, accounts, insert);
    }
    @Test void sharedPasswordCannotMatchEvenTheLastGeneratedUsername() {
        assertThrows(com.example.banking.exception.ValidationException.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, "Training_demo_100".toCharArray()));
        verifyNoInteractions(factory, hasher, accounts, insert);
    }
    @Test void cleanupFailureAfterCommitIsUncertainAndNeverReplayed() throws Exception {
        doThrow(new SQLException("PRIVATE_DRIVER_DETAIL")).when(c).close();
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertTrue(failure.uncertain()); assertNull(failure.getCause());
        verify(c).commit(); verify(c).close(); verify(insert, times(100)).executeUpdate();
    }
    @Test void anotherDatabaseProductCannotBeSeeded() throws Exception {
        when(c.getMetaData().getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        verifyNoInteractions(insert, database, count, accounts, hasher);
    }
    @Test void failedRollbackMarksWritesUncertainAndStillClosesConnection() throws Exception {
        when(accounts.insert(any(), anyLong(), any(), any(), any()))
                .thenThrow(new SQLException("PRIVATE_INSERT_DETAIL"));
        doThrow(new SQLException("PRIVATE_ROLLBACK_DETAIL")).when(c).rollback();
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertTrue(failure.uncertain());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("PRIVATE"));
        verify(c).rollback(); verify(c).close(); verify(c, never()).commit();
        verify(insert).executeUpdate();
    }
    @Test void commitAndCloseFailuresKeepUncertaintyWithoutReplayingInserts() throws Exception {
        doThrow(new SQLException("PRIVATE_COMMIT_DETAIL")).when(c).commit();
        doThrow(new SQLException("PRIVATE_CLOSE_DETAIL")).when(c).close();
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertTrue(failure.uncertain());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("PRIVATE"));
        verify(c).commit(); verify(c).rollback(); verify(c).close();
        verify(insert, times(100)).executeUpdate();
    }
    @Test void uncheckedCloseFailureAfterCommitIsSanitizedAndUncertain() throws Exception {
        doThrow(new IllegalStateException("PRIVATE_CLOSE_DETAIL")).when(c).close();
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertTrue(failure.uncertain());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("PRIVATE"));
        verify(c).commit(); verify(c).close(); verify(c, never()).rollback();
        verify(insert, times(100)).executeUpdate();
    }
    @Test void uncheckedRollbackFailureIsSanitizedAndDoesNotSkipClose() throws Exception {
        when(accounts.insert(any(), anyLong(), any(), any(), any()))
                .thenThrow(new SQLException("PRIVATE_INSERT_DETAIL"));
        doThrow(new IllegalStateException("PRIVATE_ROLLBACK_DETAIL")).when(c).rollback();
        var failure = assertThrows(DemoDataLoader.SeedFailure.class,
                () -> DemoDataLoader.seed(factory, hasher, accounts, CLOCK, 10, password));
        assertTrue(failure.uncertain());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("PRIVATE"));
        verify(c).rollback(); verify(c).close(); verify(c, never()).commit();
    }
}
