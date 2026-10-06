package org.mifos.connector.airtel.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectionRequestDtoTest {

    private static final Map<String, String> COUNTRY_CODES = Map.of("zmw", "ZM", "rwf", "RW");

    @Test
    @DisplayName("fromChannelRequest strips plus-prefixed MSISDN and applies transaction id prefix")
    void fromChannelRequest_withPlusAndPrefix() {
        JSONObject channelRequest = new JSONObject("""
                {
                  "amount": { "currency": "ZMW", "amount": 100 },
                  "payer": {
                    "partyIdInfo": { "partyIdentifier": "+260788123456" }
                  }
                }
                """);

        CollectionRequestDto dto = CollectionRequestDto.fromChannelRequest(
                channelRequest, "txn-1", COUNTRY_CODES, "oaf-");

        assertEquals("Payment to OAF", dto.getReference());
        assertEquals("ZM", dto.getSubscriber().getCountry());
        assertEquals("ZMW", dto.getSubscriber().getCurrency());
        assertEquals(788123456L, dto.getSubscriber().getMsisdn());
        assertEquals(BigDecimal.valueOf(100), dto.getTransaction().getAmount());
        assertEquals("oaf-txn-1", dto.getTransaction().getId());
        assertTrue(dto.toString().contains("oaf-txn-1"));
        assertTrue(dto.getSubscriber().toString().contains("788123456"));
        assertTrue(dto.getTransaction().toString().contains("ZMW"));
    }

    @Test
    @DisplayName("fromChannelRequest strips country code without plus and omits blank prefix")
    void fromChannelRequest_withoutPlusAndBlankPrefix() {
        JSONObject channelRequest = new JSONObject("""
                {
                  "amount": { "currency": "RWF", "amount": 50.5 },
                  "payer": {
                    "partyIdInfo": { "partyIdentifier": "250788123456" }
                  }
                }
                """);

        CollectionRequestDto dto = CollectionRequestDto.fromChannelRequest(
                channelRequest, "txn-2", COUNTRY_CODES, "  ");

        assertEquals(788123456L, dto.getSubscriber().getMsisdn());
        assertEquals("txn-2", dto.getTransaction().getId());
        assertEquals("RW", dto.getTransaction().getCountry());
    }
}
