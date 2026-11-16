package com.example.banking.unit;

import com.example.banking.model.*;
import java.math.BigDecimal;
import java.time.*;
import static com.example.banking.unit.DepositFixtures.*;

final class HistoryFixtures {
    static Account selected(long owner, AccountStatus status) {
        return new SavingsAccount(7, owner, NUMBER, Money.parse("100"), BigDecimal.ZERO,
                Money.ZERO, LocalDate.of(2026, 11, 15), status);
    }
    static HistoryQuery query(int page, int size) { return new HistoryQuery(page, size, null, null, null); }
    static HistoryEntry row(long id, Instant time) {
        return new HistoryEntry(id, new TransactionReference("TX-20261115-" + String.format("%028X", id)),
                TransactionType.TRANSFER, Money.parse("0.25"), HistoryEntry.Direction.DEBIT, TransactionStatus.SUCCESS, time);
    }
    private HistoryFixtures() {}
}

