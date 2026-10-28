package com.example.banking.model;

/** Baseline ledger writes should use SUCCESS only for committed movements. */
public enum TransactionStatus { PENDING, SUCCESS, FAILED, REVERSED }
