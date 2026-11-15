package com.example.banking.exception;

/** Never replay an INSERT when commit or connection cleanup may have succeeded. */
public final class AccountOpeningUnconfirmedException extends BankingException {
    public AccountOpeningUnconfirmedException() {
        super("Account opening could not be confirmed. Do not open another account or restart to retry. "
                + "List your accounts and ask an administrator to reconcile the result.");
    }
}
