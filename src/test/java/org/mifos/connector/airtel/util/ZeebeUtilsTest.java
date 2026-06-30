package org.mifos.connector.airtel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZeebeUtilsTest {

    @DisplayName("getNextTimer doubles the exponent for ISO-8601 second durations")
    @Test
    void getNextTimer_doublesDurationExponent() {
        assertEquals("PT2S", ZeebeUtils.getNextTimer("PT1S"));
        assertEquals("PT4S", ZeebeUtils.getNextTimer("PT2S"));
        assertEquals("PT8S", ZeebeUtils.getNextTimer("PT4S"));
        assertEquals("PT64S", ZeebeUtils.getNextTimer("PT45S"));
    }

    @DisplayName("getTransferResponseCreateJson includes completedTimestamp")
    @Test
    void getTransferResponseCreateJson_includesCompletedTimestamp() {
        JsonObject jsonObject = ZeebeUtils.getTransferResponseCreateJson();

        assertNotNull(jsonObject.get("completedTimestamp"));
        assertFalse(jsonObject.get("completedTimestamp").toString().isBlank());
    }
}
