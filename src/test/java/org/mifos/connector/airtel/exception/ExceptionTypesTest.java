package org.mifos.connector.airtel.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExceptionTypesTest {

    @DisplayName("WorkflowNotFoundException exposes message")
    @Test
    void workflowNotFoundException_exposesMessage() {
        WorkflowNotFoundException exception =
            new WorkflowNotFoundException("No workflow instance found");

        assertEquals("No workflow instance found", exception.getMessage());
    }

    @DisplayName("TransactionAlreadyExistsException exposes message")
    @Test
    void transactionAlreadyExistsException_exposesMessage() {
        TransactionAlreadyExistsException exception =
            new TransactionAlreadyExistsException("Transaction already exists");

        assertEquals("Transaction already exists", exception.getMessage());
    }
}
