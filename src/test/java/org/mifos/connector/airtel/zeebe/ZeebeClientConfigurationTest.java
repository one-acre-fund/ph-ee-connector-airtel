package org.mifos.connector.airtel.zeebe;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.camunda.zeebe.client.ZeebeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ZeebeClientConfigurationTest {

    @Test
    @DisplayName("setup builds a Zeebe client from configured broker contact point")
    void setup_buildsClient() {
        ZeebeClientConfiguration configuration = new ZeebeClientConfiguration();
        ReflectionTestUtils.setField(configuration, "zeebeBrokerContactPoint", "localhost:26500");
        ReflectionTestUtils.setField(configuration, "zeebeClientMaxThreads", 2);

        ZeebeClient client = configuration.setup();
        assertNotNull(client);
        client.close();
    }
}
