package org.mifos.connector.airtel.store;

/**
 * Correlation store for paybill workflow instance keys keyed by Airtel transaction id.
 */
public interface PaybillStateStore {

    void putWorkflowInstance(String txnId, String workflowInstanceKey);

    String getWorkflowInstance(String txnId);

    void removeWorkflowInstance(String txnId);

    /**
     * Atomically reads and removes the workflow instance for {@code txnId}.
     * A concurrent caller receives {@code null} after the first successful consume.
     *
     * @return the workflow instance key, or {@code null} if missing/expired
     */
    String consumeWorkflowInstance(String txnId);
}
