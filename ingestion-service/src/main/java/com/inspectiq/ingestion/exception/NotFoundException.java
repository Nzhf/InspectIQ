package com.inspectiq.ingestion.exception;

/** Thrown when a referenced resource (batch, defect type) does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}