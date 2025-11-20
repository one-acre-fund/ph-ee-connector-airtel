package org.mifos.connector.airtel;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.mifos.connector.common.gsma.dto.CustomData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AIRTEL_CONSTANT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.PAYMENT_SCHEME;

public class AirtelUtilsTest {

    @Test
    void testCreateCustomData_containsPaymentScheme() {
        ChannelValidationResponse response = new ChannelValidationResponse(true, "AMS",
                "tenant-1", "txn-1", "100", "USD", "12345",
                "John Doe", List.of(), "Validation successful");
        List<CustomData> customDataList = AirtelUtils.createCustomData(response, "shortCode", "PT1H");
        boolean found = customDataList.stream()
            .anyMatch(cd -> PAYMENT_SCHEME.equals(cd.getKey()) && AIRTEL_CONSTANT.equals(cd.getValue()));

        assertTrue(found, "CustomData should contain paymentScheme with value AIRTEL_CONSTANT");
    }

    @DisplayName("getCountryFromExchange returns country when property is set")
    @Test
    void test_getCountryFromExchange_with_property_set() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        String result = AirtelUtils.getCountryFromExchange(exchange);
        assertEquals("rwanda", result);
    }

    @DisplayName("getCountryFromExchange returns default tenant when property is missing")
    @Test
    void test_getCountryFromExchange_with_property_missing() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        String result = AirtelUtils.getCountryFromExchange(exchange);
        assertEquals("rwanda", result);
    }

    @DisplayName("getCountryFromCurrency returns 'zambia' for ZMW")
    @Test
    void test_getCountryFromCurrency_with_ZMW() {
        assertEquals("zambia", AirtelUtils.getCountryFromCurrency("ZMW"));
    }

    @DisplayName("getCountryFromCurrency returns 'rwanda' for non-ZMW currency")
    @Test
    void test_getCountryFromCurrency_with_non_ZMW() {
        assertEquals("rwanda", AirtelUtils.getCountryFromCurrency("EUR"));
        assertEquals("rwanda", AirtelUtils.getCountryFromCurrency("USD"));
        assertEquals("rwanda", AirtelUtils.getCountryFromCurrency("RWF"));
    }

}
