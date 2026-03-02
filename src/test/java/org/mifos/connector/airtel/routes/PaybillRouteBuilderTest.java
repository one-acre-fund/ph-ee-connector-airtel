package org.mifos.connector.airtel.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CONFIRMATION_REQUEST_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.AirtelMoneyConnectorApplicationTests;
import org.mifos.connector.airtel.dto.AirtelConfirmationRequest;
import org.mifos.connector.common.mojaloop.type.TransferState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests for the paybill-transaction-status-check-base route in
 * {@link org.mifos.connector.airtel.camel.routes.PaybillRouteBuilder},
 * verifying that PLATFORM_TENANT_ID is set via
 * {@code airtelUtils.getCountryFromCurrency()} (line 192).
 */
@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
class PaybillRouteBuilderTest extends AirtelMoneyConnectorApplicationTests {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    private static final String ROUTE_ID = "paybill-transaction-status-check-base";

    /**
     * Build a minimal {@link AirtelConfirmationRequest} with the given currency.
     */
    private AirtelConfirmationRequest buildConfirmationRequest(String currency) {
        return new AirtelConfirmationRequest(
                "txn-100", BigDecimal.valueOf(500), currency,
                "260788000000", "ACC-001", "short-code-1");
    }

    /**
     * Build a dummy channel transfer response body that can be unmarshalled
     * to {@code TransactionStatusResponseDTO}.
     */
    private String dummyTransferStatusResponse(String transactionId) {
        return "{"
                + "\"transactionId\":\"" + transactionId + "\","
                + "\"transferState\":\"COMMITTED\","
                + "\"completedTimestamp\":\"2026-02-27T12:00:00\""
                + "}";
    }

    @DisplayName("paybill-transaction-status-check-base sets PLATFORM_TENANT_ID via airtelUtils.getCountryFromCurrency (line 192)")
    @Test
    void testPlatformTenantIdSetFromCurrency() throws Exception {
        // Replace the toD HTTP call with a mock endpoint that returns a valid JSON body
        AdviceWithRouteBuilder.adviceWith(camelContext, ROUTE_ID, a -> {
            a.weaveByType(org.apache.camel.model.ToDynamicDefinition.class)
                    .replace()
                    .process(exchange -> {
                        String txnId = exchange.getIn().getHeader("transactionId", String.class);
                        exchange.getIn().setBody(dummyTransferStatusResponse(txnId));
                    })
                    .to("mock:channel-transfer");
        });

        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        MockEndpoint mockEndpoint = camelContext.getEndpoint("mock:channel-transfer",
                MockEndpoint.class);

        // --- Case 1: ZMW currency -> "zambia" ---
        mockEndpoint.expectedMessageCount(1);

        Exchange zmwExchange = camelContext.getEndpoint("direct:" + ROUTE_ID).createExchange();
        zmwExchange.setProperty(CONFIRMATION_REQUEST_BODY, buildConfirmationRequest("ZMW"));
        zmwExchange.getIn().setHeader("transactionId", "txn-100");

        producerTemplate.send("direct:" + ROUTE_ID, zmwExchange);

        mockEndpoint.assertIsSatisfied();
        // getCountryFromCurrency("ZMW") returns "zambia" per application.yml
        assertEquals("zambia", mockEndpoint.getExchanges().get(0).getIn()
                .getHeader(PLATFORM_TENANT_ID));

        // --- Case 2: RWF currency (not in map) -> default "rwanda" ---
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        Exchange rwfExchange = camelContext.getEndpoint("direct:" + ROUTE_ID).createExchange();
        rwfExchange.setProperty(CONFIRMATION_REQUEST_BODY, buildConfirmationRequest("RWF"));
        rwfExchange.getIn().setHeader("transactionId", "txn-200");

        producerTemplate.send("direct:" + ROUTE_ID, rwfExchange);

        mockEndpoint.assertIsSatisfied();
        // getCountryFromCurrency("RWF") falls back to "rwanda"
        assertEquals("rwanda", mockEndpoint.getExchanges().get(0).getIn()
                .getHeader(PLATFORM_TENANT_ID));
    }
}
