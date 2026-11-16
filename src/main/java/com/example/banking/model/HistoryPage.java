package com.example.banking.model;

import java.util.List;
import java.util.Objects;

/** Immutable page, with one extra row used only to determine whether a next page exists. */
public record HistoryPage(long accountId, AccountNumber number, HistoryQuery query,
                          List<HistoryEntry> entries, boolean hasNext) {
    public HistoryPage {
        Objects.requireNonNull(number);
        Objects.requireNonNull(query);
        entries = List.copyOf(entries);
        if (accountId <= 0 || entries.size() > query.pageSize() || (hasNext && entries.size() != query.pageSize()))
            throw new IllegalArgumentException("History page shape is invalid.");
    }
}

