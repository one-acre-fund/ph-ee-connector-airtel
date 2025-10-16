package org.mifos.connector.airtel;

import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.mifos.connector.common.gsma.dto.CustomData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.PAYMENT_SCHEME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AIRTEL_CONSTANT;

class AirtelUtilsTest {

    @Test
    void testCreateCustomData_containsPaymentScheme() {
        ChannelValidationResponse response = new ChannelValidationResponse(true, "AMS", "tenant-1", "txn-1", "100", "USD", "12345", "John Doe", List.of(), "Validation successful");
        List<CustomData> customDataList = AirtelUtils.createCustomData(response, "shortCode", "PT1H");

        boolean found = customDataList.stream()
            .anyMatch(cd -> PAYMENT_SCHEME.equals(cd.getKey()) && AIRTEL_CONSTANT.equals(cd.getValue()));

        assertTrue(found, "CustomData should contain paymentScheme with value AIRTEL_CONSTANT");
    }
}
