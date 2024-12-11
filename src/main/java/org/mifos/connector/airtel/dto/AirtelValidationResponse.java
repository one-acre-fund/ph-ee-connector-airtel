package org.mifos.connector.airtel.dto;

/**
 * Airtel validation response DTO.
 *
 * @param message       response message
 * @param transactionId transaction id
 * @param clientName    client's full name
 */
public record AirtelValidationResponse(String message, String transactionId, String clientName) {
}
