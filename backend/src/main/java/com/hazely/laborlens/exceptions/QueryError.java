package com.hazely.laborlens.exceptions;

/** A safe client-facing validation error, separate from internal query failures. */
public class QueryError extends RuntimeException {
    private final String code;
    private final String field;
    public QueryError(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }
    public String getCode() { return code; }
    public String getField() { return field; }
}
