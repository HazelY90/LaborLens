package com.hazely.laborlens.exceptions;

/** Safe authentication errors never include passwords, tokens or database details. */
public class AuthError extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    public AuthError(int status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public static AuthError unauthorized() {
        return new AuthError(401, "UNAUTHENTICATED", "Valid authentication is required.", null);
    }
}
