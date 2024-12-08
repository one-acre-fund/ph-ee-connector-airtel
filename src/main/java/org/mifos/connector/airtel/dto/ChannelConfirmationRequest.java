package org.mifos.connector.airtel.dto;

import static org.mifos.connector.airtel.camel.config.CamelProperties.SECONDARY_IDENTIFIER_NAME;

import org.json.JSONObject;

/**
 * DTO to be used by the channel connector to confirm a payment.
 */
public record ChannelConfirmationRequest(JSONObject payer, JSONObject payee, JSONObject amount) {

    /**
     * Create a {@link ChannelConfirmationRequest} from an {@link AirtelConfirmationRequest}.
     *
     * @param request  the AirtelConfirmationRequest to create the ChannelConfirmationRequest from
     * @param amsProps the properties of the AMS
     * @return {@link ChannelConfirmationRequest}
     */
    public static ChannelConfirmationRequest fromPaybillConfirmation(
        AirtelConfirmationRequest request, PaybillProps.AmsProps amsProps) {
        JSONObject payer = new JSONObject();
        JSONObject partyIdInfoPayer = new JSONObject();
        partyIdInfoPayer.put("partyIdType", SECONDARY_IDENTIFIER_NAME);
        partyIdInfoPayer.put("partyIdentifier", request.msisdn());
        payer.put("partyIdInfo", partyIdInfoPayer);

        JSONObject payee = new JSONObject();
        JSONObject partyIdInfoPayee = new JSONObject();
        partyIdInfoPayee.put("partyIdType", amsProps.getIdentifier());
        partyIdInfoPayee.put("partyIdentifier", request.accountNumber());
        payee.put("partyIdInfo", partyIdInfoPayee);

        JSONObject amount = new JSONObject();
        amount.put("amount", request.amount().toString());
        amount.put("currency", request.currency());
        return new ChannelConfirmationRequest(payer, payee, amount);
    }

    public String toString() {
        return "{" + "payer:" + payer + ", payee:" + payee + ", amount:" + amount + "}";
    }
}
