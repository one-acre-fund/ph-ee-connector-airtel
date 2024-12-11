package org.mifos.connector.airtel.dto;

import java.util.List;
import org.mifos.connector.common.gsma.dto.CustomData;

/**
 * Channel validation response DTO.
 *
 * @param reconciled                  whether the validation is successful
 * @param amsName                     the AMS name
 * @param accountHoldingInstitutionId the account holding institution ID
 * @param transactionId               the transaction ID
 * @param amount                      the transaction amount
 * @param currency                    the transaction currency
 * @param msisdn                      the client's phone number
 * @param clientName                  the client's full name
 * @param customData                  custom data
 */
public record ChannelValidationResponse(
    boolean reconciled,
    String amsName,
    String accountHoldingInstitutionId,
    String transactionId,
    String amount,
    String currency,
    String msisdn,
    String clientName,
    List<CustomData> customData
) { }
