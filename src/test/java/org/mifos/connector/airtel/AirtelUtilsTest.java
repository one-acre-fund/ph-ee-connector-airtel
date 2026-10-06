package org.mifos.connector.airtel;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.mifos.connector.airtel.util.CountryProps;
import org.mifos.connector.common.gsma.dto.CustomData;
import org.mifos.connector.common.gsma.dto.GsmaTransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AIRTEL_CONSTANT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CLIENT_CORRELATION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CONFIRMATION_TIMER;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INITIATOR_FSP_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.PARTY_LOOKUP_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.PAYMENT_SCHEME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

public class AirtelUtilsTest {

    private AirtelUtils airtelUtils;

    @BeforeEach
    void setUp() {
        CountryProps countryProps = new CountryProps();
        countryProps.setCurrency(Map.of("ZMW", "zambia", "MWK", "malawi"));
        countryProps.setDefaultTenant("rwanda");
        airtelUtils = new AirtelUtils(countryProps);
    }

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
        String result = airtelUtils.getCountryFromExchange(exchange);
        assertEquals("rwanda", result);
    }

    @DisplayName("getCountryFromExchange returns default tenant when property is missing")
    @Test
    void test_getCountryFromExchange_with_property_missing() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        String result = airtelUtils.getCountryFromExchange(exchange);
        assertEquals("rwanda", result);
    }

    @DisplayName("getCountryFromCurrency returns 'zambia' for ZMW")
    @Test
    void test_getCountryFromCurrency_with_ZMW() {
        assertEquals("zambia", airtelUtils.getCountryFromCurrency("ZMW"));
    }

    @DisplayName("getCountryFromCurrency returns 'rwanda' for non-ZMW currency")
    @Test
    void test_getCountryFromCurrency_with_non_ZMW() {
        assertEquals("rwanda", airtelUtils.getCountryFromCurrency("EUR"));
        assertEquals("rwanda", airtelUtils.getCountryFromCurrency("USD"));
        assertEquals("rwanda", airtelUtils.getCountryFromCurrency("RWF"));
    }

    @DisplayName("getCountryFromCurrency returns 'malawi' for MWK")
    @Test
    void test_getCountryFromCurrency_with_MWK() {
        assertEquals("malawi", airtelUtils.getCountryFromCurrency("MWK"));
    }

    @DisplayName("createGsmaTransferRequest maps validation response into GSMA payload")
    @Test
    void createGsmaTransferRequest_mapsValidationResponse() {
        ChannelValidationResponse response = new ChannelValidationResponse(true, "fineract",
            "tenant-1", "txn-1", "100", "ZMW", "260788000000",
            "John Doe", List.of(), "Validation successful");

        GsmaTransfer transfer = AirtelUtils.createGsmaTransferRequest(
            response, "short-code", "accountnumber", "ACC-001", "PT45S");

        assertEquals("airtel", transfer.getType());
        assertEquals("inbound", transfer.getSubType());
        assertEquals("100", transfer.getAmount());
        assertEquals("ZMW", transfer.getCurrency());
        assertEquals("txn-1", transfer.getRequestingOrganisationTransactionReference());
        assertEquals("260788000000", transfer.getPayer().get(0).getPartyIdIdentifier());
        assertEquals("ACC-001", transfer.getPayee().get(0).getPartyIdIdentifier());
        assertNotNull(transfer.getRequestDate());

        assertTrue(transfer.getCustomData().stream()
            .anyMatch(cd -> TRANSACTION_ID.equals(cd.getKey()) && "txn-1".equals(cd.getValue())));
        assertTrue(transfer.getCustomData().stream()
            .anyMatch(cd -> CLIENT_CORRELATION_ID.equals(cd.getKey()) && "txn-1".equals(cd.getValue())));
        assertTrue(transfer.getCustomData().stream()
            .anyMatch(cd -> INITIATOR_FSP_ID.equals(cd.getKey()) && "short-code".equals(cd.getValue())));
        assertTrue(transfer.getCustomData().stream()
            .anyMatch(cd -> CONFIRMATION_TIMER.equals(cd.getKey()) && "PT45S".equals(cd.getValue())));
    }

    @DisplayName("createCustomData sets partyLookupFailed true when not reconciled")
    @Test
    void createCustomData_unreconciled_setsPartyLookupFailed() {
        ChannelValidationResponse response = new ChannelValidationResponse(false, "AMS",
                "tenant-1", "txn-1", "100", "USD", "12345",
                "John Doe", List.of(), "failed");
        List<CustomData> customDataList = AirtelUtils.createCustomData(response, "shortCode", "PT1H");
        assertTrue(customDataList.stream()
                .anyMatch(cd -> PARTY_LOOKUP_FAILED.equals(cd.getKey())
                        && Boolean.TRUE.equals(cd.getValue())));
    }

    @DisplayName("getDefaultTenant returns configured default tenant")
    @Test
    void getDefaultTenant_returnsConfiguredValue() {
        assertEquals("rwanda", airtelUtils.getDefaultTenant());
    }
}
