package com.example.banking.model.policy;

import com.example.banking.exception.ValidationException;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** At least 10 characters, all four classes, and at most 72 UTF-8 bytes. */
public final class PasswordPolicy {
    public void validate(String username, char[] raw) {
        if (!hasSupportedLength(raw) || Character.codePointCount(raw, 0, raw.length) < 10) {
            throw new ValidationException("Password must have at least 10 characters and at most 72 UTF-8 bytes.");
        }
        boolean upper = false, lower = false, digit = false, special = false;
        for (int i = 0; i < raw.length;) {
            int point = Character.codePointAt(raw, i);
            if (Character.isISOControl(point) || Character.isWhitespace(point)) {
                throw new ValidationException("Password must not contain whitespace or control characters.");
            }
            upper |= Character.isUpperCase(point);
            lower |= Character.isLowerCase(point);
            digit |= Character.isDigit(point);
            special |= !Character.isLetterOrDigit(point);
            i += Character.charCount(point);
        }
        boolean equalsUsername = username != null && username.length() == raw.length;
        if (equalsUsername) {
            for (int i = 0; i < raw.length; i++) {
                equalsUsername &= Character.toLowerCase(raw[i])
                        == Character.toLowerCase(username.charAt(i));
            }
        }
        if (!upper || !lower || !digit || !special || equalsUsername) {
            throw new ValidationException(
                    "Password needs uppercase, lowercase, a digit and a symbol, and must differ from the username.");
        }
    }

    /** No String copy and no bcrypt truncation, including for login input. */
    public static boolean hasSupportedLength(char[] raw) {
        if (raw == null || raw.length == 0 || raw.length > 72) return false;
        java.nio.ByteBuffer encoded = null;
        try {
            encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(raw));
            return encoded.remaining() <= 72;
        } catch (CharacterCodingException invalidUnicode) {
            return false;
        } finally {
            if (encoded != null && encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        }
    }
}

