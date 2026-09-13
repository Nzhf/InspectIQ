package com.inspectiq.ingestion.exception;

/** Thrown when a request violates a business rule (e.g. duplicate batch code). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}