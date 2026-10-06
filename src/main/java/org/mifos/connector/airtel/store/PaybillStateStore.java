package org.mifos.connector.airtel.store;

/**
 * Correlation store for paybill workflow instance keys keyed by Airtel transaction id.
 */
public interface PaybillStateStore {

    void putWorkflowInstance(String txnId, String workflowInstanceKey);

    String getWorkflowInstance(String txnId);

    void removeWorkflowInstance(String txnId);
}
