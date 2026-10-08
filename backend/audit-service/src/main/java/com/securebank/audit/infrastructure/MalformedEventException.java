package com.securebank.audit.infrastructure;

/** A Kafka payload that can never be processed (bad JSON, missing required fields). Not retried. */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
