package org.mifos.connector.airtel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectionUtilsTest {

    @Test
    @DisplayName("getConnectionTimeoutDsl substitutes timeout into all three httpClient params")
    void getConnectionTimeoutDsl_substitutesTimeout() {
        String dsl = ConnectionUtils.getConnectionTimeoutDsl(6000);
        assertEquals(
                "httpClient.connectTimeout=6000&httpClient.connectionRequestTimeout=6000"
                        + "&httpClient.socketTimeout=6000",
                dsl);
        assertTrue(dsl.contains("6000"));
    }
}
