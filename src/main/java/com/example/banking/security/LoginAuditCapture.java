package com.example.banking.security;

import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;

import java.time.Instant;

/**
 * Explicit, lossy capture policy for rejected oversized login usernames.
 * This is neither registration validation nor authentication or persistence.
 */
public final class LoginAuditCapture {

    private LoginAuditCapture() {
    }

    /**
     * Creates unsaved failure evidence without retaining any part or digest of
     * the oversized input. The marker is not an attempted username: interpret it
     * together with the reserved USERNAME_TOO_LONG reason code.
     *
     * <p>Future callers must select this path before lookup/password verification
     * when the username exceeds 80 Unicode code points, deny authentication with
     * the usual generic response, and append the returned record independently.
     * Never supply credentials here or log the raw input. Inputs within the limit
     * belong in the ordinary LoginAudit constructor, unchanged.
     *
     * @param usernameAttempted non-null username of more than 80 code points
     * @param createdAt required caller-supplied attempt time
     * @return FAILURE with unknown customer and a fixed, non-identifying marker
     */
    public static LoginAudit failedOversizedUsername(String usernameAttempted, Instant createdAt) {
        if (usernameAttempted == null
                || usernameAttempted.codePointCount(0, usernameAttempted.length()) <= 80) {
            throw new IllegalArgumentException("An oversized attempted username is required.");
        }
        return new LoginAudit(null, null, "[OVERSIZED_USERNAME]", LoginOutcome.FAILURE,
                "USERNAME_TOO_LONG", createdAt);
    }
}
