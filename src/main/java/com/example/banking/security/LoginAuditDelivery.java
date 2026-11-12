package com.example.banking.security;

import com.example.banking.dao.LoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;

/** One-shot audit delivery; never retries an uncertain save. */
public final class LoginAuditDelivery {

    private final LoginAuditDAO audits;

    public LoginAuditDelivery(LoginAuditDAO audits) {
        if (audits == null) {
            throw new IllegalArgumentException(
                    "Login audit writer is required.");
        }
        this.audits = audits;
    }

    public enum Status {
        SAVED,
        REJECTED_NO_WRITE,
        SAVE_UNCERTAIN
    }

    public record Result(LoginAudit evidence, Status status) {

        public Result {
            if (evidence == null || status == null) {
                throw new IllegalArgumentException(
                        "Evidence and delivery status are required.");
            }
        }

        @Override
        public String toString() {
            return "LoginAuditDelivery.Result[status=" + status + "]";
        }
    }

    public Result deliver(LoginAudit evidence) {
        if (evidence == null || evidence.auditId().isPresent()) {
            throw new IllegalArgumentException(
                    "Unsaved login audit evidence is required.");
        }

        Status status;

        try {
            audits.append(evidence);
            status = Status.SAVED;
        } catch (IllegalArgumentException rejected) {
            status = Status.REJECTED_NO_WRITE;
        } catch (PersistenceException uncertain) {
            status = Status.SAVE_UNCERTAIN;
        }

        return new Result(evidence, status);
    }
}