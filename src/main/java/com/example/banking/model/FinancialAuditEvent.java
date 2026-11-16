package com.example.banking.model;

/** Evidence includes non-cash lifecycle operations; the cash ledger enum deliberately does not. */
public enum FinancialAuditEvent { DEPOSIT, WITHDRAWAL, TRANSFER, LOAN_REPAYMENT, LOAN_CREATION }

