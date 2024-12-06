package org.mifos.connector.airtel.dto;

import java.math.BigDecimal;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Airtel confirmation request DTO.
 *
 * @param transactionId     the transaction id
 * @param amount            the amount
 * @param currency          the currency
 * @param msisdn            the client's phone number
 * @param accountNumber     the client's account number
 * @param businessShortCode the business short code
 */
public record AirtelConfirmationRequest(
    @NotBlank String transactionId,
    @NotNull BigDecimal amount,
    @NotBlank @Size(min = 3, max = 3, message = "must be 3 characters") String currency,
    @NotBlank String msisdn,
    @NotBlank String accountNumber,
    String businessShortCode
) {}
