package org.mifos.connector.airtel.dto;

import static org.mifos.connector.airtel.camel.config.CamelProperties.CURRENCY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.GET_ACCOUNT_DETAILS_FLAG;
import static org.mifos.connector.airtel.camel.config.CamelProperties.SECONDARY_IDENTIFIER_NAME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

import java.util.List;
import org.mifos.connector.common.gsma.dto.CustomData;

/**
 * Represents a channel validation request DTO.
 */
public record ChannelValidationRequest(
    CustomData primaryIdentifier,
    CustomData secondaryIdentifier,
    List<CustomData> customData) {

    /**
     * Creates a channel validation request payload from an Airtel validation request.
     *
     * @param request  the Airtel validation request
     * @param amsProps the AMS properties
     * @return {@link ChannelValidationRequest}
     */
    public static ChannelValidationRequest fromPaybillValidation(AirtelValidationRequest request,
                                                                 PaybillProps.AmsProps amsProps) {
        CustomData primaryIdentifier = new CustomData(amsProps.getIdentifier(),
            request.accountNumber());
        CustomData secondaryIdentifier = new CustomData(SECONDARY_IDENTIFIER_NAME,
            request.msisdn());
        CustomData transactionId = new CustomData(TRANSACTION_ID, request.transactionId());
        CustomData currency = new CustomData(CURRENCY, request.currency());
        CustomData getAccountDetails = new CustomData(GET_ACCOUNT_DETAILS_FLAG, Boolean.TRUE);
        return new ChannelValidationRequest(primaryIdentifier, secondaryIdentifier,
            List.of(transactionId, currency, getAccountDetails));
    }
}
