package org.mifos.connector.airtel.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Airtel validation request DTO.
 *
 * @param transactionId     unique transaction id
 * @param accountNumber     client's account number
 * @param businessShortCode business short code
 * @param currency          currency
 * @param msisdn            client's phone number
 */
public record AirtelValidationRequest(
    @NotBlank String transactionId,
    @NotBlank String accountNumber,
    String businessShortCode,
    @NotBlank @Size(min = 3, max = 3, message = "must be 3 characters") String currency,
    @NotBlank String msisdn

) {}
