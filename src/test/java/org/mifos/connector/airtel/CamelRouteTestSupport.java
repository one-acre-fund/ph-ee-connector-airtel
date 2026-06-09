package org.mifos.connector.airtel;

import org.apache.camel.CamelContext;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base support for Camel route tests using advice-with and a random server port.
 */
@UseAdviceWith
public abstract class CamelRouteTestSupport extends AirtelMoneyConnectorApplicationTests {

    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate producerTemplate;

    @Autowired
    protected FluentProducerTemplate fluentProducerTemplate;

    @BeforeEach
    void startCamelContextAndTemplates() throws Exception {
        if (!camelContext.isStarted()) {
            camelContext.start();
        }
        producerTemplate.start();
        fluentProducerTemplate.start();
    }
}
