package com.hazely.laborlens.exceptions;

import com.hazely.laborlens.controllers.DataController;
import com.hazely.laborlens.dtos.DataDtos.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Keep internal persistence failures out of public error responses. */
@RestControllerAdvice(assignableTypes = DataController.class)
public class DataErrors {
    private static final Logger log = LoggerFactory.getLogger(DataErrors.class);
    @ExceptionHandler(QueryError.class)
    public ResponseEntity<ApiError> invalid(QueryError error) {
        return ResponseEntity.badRequest().body(new ApiError(error.getCode(), error.getMessage(), error.getField()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        log.error("Data query failed", error);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "Unable to load data.", null));
    }
}
