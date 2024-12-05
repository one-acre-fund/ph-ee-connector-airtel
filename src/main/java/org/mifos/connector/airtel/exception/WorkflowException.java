package org.mifos.connector.airtel.exception;

/**
 * Exception thrown when there is an error with running a workflow.
 */
public class WorkflowException extends RuntimeException {
    public WorkflowException(String message) {
        super(message);
    }
}
