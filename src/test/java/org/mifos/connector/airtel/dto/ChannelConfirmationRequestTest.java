package org.mifos.connector.airtel.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelConfirmationRequestTest {

    @DisplayName("fromPaybillConfirmation maps payer, payee and amount fields")
    @Test
    void fromPaybillConfirmation_mapsFields() {
        AirtelConfirmationRequest request = new AirtelConfirmationRequest(
            "txn-1", BigDecimal.valueOf(250), "ZMW",
            "260788000000", "ACC-001", "short-code-1");

        PaybillProps.AmsProps amsProps = new PaybillProps.AmsProps();
        amsProps.setIdentifier("accountnumber");

        ChannelConfirmationRequest channelRequest =
            ChannelConfirmationRequest.fromPaybillConfirmation(request, amsProps);

        assertEquals("260788000000",
            channelRequest.payer().getJSONObject("partyIdInfo").getString("partyIdentifier"));
        assertEquals("MSISDN",
            channelRequest.payer().getJSONObject("partyIdInfo").getString("partyIdType"));
        assertEquals("ACC-001",
            channelRequest.payee().getJSONObject("partyIdInfo").getString("partyIdentifier"));
        assertEquals("accountnumber",
            channelRequest.payee().getJSONObject("partyIdInfo").getString("partyIdType"));
        assertEquals("250", channelRequest.amount().getString("amount"));
        assertEquals("ZMW", channelRequest.amount().getString("currency"));
        assertTrue(channelRequest.toString().contains("payer:"));
    }
}
