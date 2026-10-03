package com.hazely.laborlens.exceptions;

import com.hazely.laborlens.controllers.AuthController;
import com.hazely.laborlens.dtos.DataDtos.ApiError;
import com.hazely.laborlens.security.RefreshCookie;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validation errors report field names but never rejected credential values. */
@RestControllerAdvice(assignableTypes = AuthController.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthErrors {
    private static final Logger log = LoggerFactory.getLogger(AuthErrors.class);
    private final RefreshCookie cookies;

    public AuthErrors(RefreshCookie cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(AuthError.class)
    public ResponseEntity<ApiError> auth(AuthError error) {
        var response = ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore());
        if (error.getStatus() == 401) response.header(HttpHeaders.SET_COOKIE, cookies.clear());
        return response.body(new ApiError(error.getCode(), error.getMessage(), error.getField()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid request field.",
                field == null ? null : field.getField()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> json() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "A valid JSON body is required.",
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        // Database exceptions can contain user input; log only the exception type here.
        log.error("Authentication operation failed: {}", error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "Unable to complete the request.",
                null));
    }
}
