package org.mifos.connector.airtel.exception;

/**
 * Exception thrown when a transaction already exists.
 */
public class TransactionAlreadyExistsException extends RuntimeException {
    public TransactionAlreadyExistsException(String message) {
        super(message);
    }
}
