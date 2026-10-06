package org.mifos.connector.airtel.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaybillPropsTest {

    private PaybillProps paybillProps;

    @BeforeEach
    void setUp() {
        PaybillProps.AmsProps defaultAms = new PaybillProps.AmsProps();
        defaultAms.setAmsName("fineract");
        defaultAms.setIdentifier("accountnumber");

        PaybillProps.AmsProps zambiaAms = new PaybillProps.AmsProps();
        zambiaAms.setAmsName("fineract");
        zambiaAms.setIdentifier("accountnumber");

        paybillProps = new PaybillProps();
        paybillProps.setDefaultShortCode("default-code");
        paybillProps.setAmsShortCodes(Map.of(
            "default-code", defaultAms,
            "zambia-code", zambiaAms
        ));
    }

    @DisplayName("getAmsProps returns configured short code when present")
    @Test
    void getAmsProps_returnsConfiguredShortCode() {
        PaybillProps.AmsProps amsProps = paybillProps.getAmsProps("zambia-code");

        assertEquals("zambia-code", amsProps.getBusinessShortCode());
        assertEquals("fineract", amsProps.getAmsName());
    }

    @DisplayName("getAmsProps falls back to default short code when code is unknown")
    @Test
    void getAmsProps_fallsBackToDefaultShortCode() {
        PaybillProps.AmsProps amsProps = paybillProps.getAmsProps("unknown-code");

        assertEquals("default-code", amsProps.getBusinessShortCode());
        assertEquals("fineract", amsProps.getAmsName());
    }

    @DisplayName("getAmsProps falls back when short code is null or blank")
    @Test
    void getAmsProps_fallsBackForNullOrBlank() {
        assertEquals("default-code", paybillProps.getAmsProps(null).getBusinessShortCode());
        assertEquals("default-code", paybillProps.getAmsProps("  ").getBusinessShortCode());
    }

    @DisplayName("PaybillProps accessors round-trip configured values")
    @Test
    void accessors_roundTrip() {
        paybillProps.setAccountHoldingInstitutionId("oaf");
        paybillProps.setTimer("PT45S");
        PaybillProps.AmsProps ams = paybillProps.getAmsProps("default-code");
        ams.setAmsUrl("http://ams");

        assertEquals("oaf", paybillProps.getAccountHoldingInstitutionId());
        assertEquals("PT45S", paybillProps.getTimer());
        assertEquals("default-code", paybillProps.getDefaultShortCode());
        assertEquals("http://ams", ams.getAmsUrl());
        assertEquals("accountnumber", ams.getIdentifier());
    }
}
