package org.mifos.connector.airtel.dto;

import org.mifos.connector.common.mojaloop.type.TransferState;

/**
 * Transaction status response DTO.
 *
 * @param message the response message
 * @param status {@link TransferState}
 * @param transactionId the transaction ID
 */
public record TransactionStatusResponse(String message, TransferState status,
                                        String transactionId) {
}
