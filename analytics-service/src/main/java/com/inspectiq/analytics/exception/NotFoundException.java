package com.inspectiq.analytics.exception;

/** Thrown when a referenced resource (e.g. a batch filter) does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}