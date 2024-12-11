package org.mifos.connector.airtel.exception;

/**
 * Exception thrown when a workflow is not found.
 */
public class WorkflowNotFoundException extends RuntimeException {
    public WorkflowNotFoundException(String message) {
        super(message);
    }
}
