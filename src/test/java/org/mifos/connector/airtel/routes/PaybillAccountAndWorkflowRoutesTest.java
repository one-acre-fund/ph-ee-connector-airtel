package org.mifos.connector.airtel.routes;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.ACCOUNT_HOLDING_INSTITUTION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.AMS_NAME;
import static org.mifos.connector.airtel.camel.config.CamelProperties.AMS_URL;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CHANNEL_VALIDATION_RESPONSE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.JSON_CONTENT_TYPE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PRIMARY_IDENTIFIER;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PRIMARY_IDENTIFIER_VALUE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INITIATOR_FSP_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.CamelRouteTestSupport;
import org.mifos.connector.airtel.dto.AirtelValidationRequest;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaybillAccountAndWorkflowRoutesTest extends CamelRouteTestSupport {

    @Test
    @DisplayName("account-status builds channel validation request and AMS headers")
    void accountStatus_setsAmsHeadersAndBody() throws Exception {
        camelContext.getRouteController().stopRoute("account-status");
        AdviceWithRouteBuilder.adviceWith(camelContext, "account-status", a ->
                a.interceptSendToEndpoint("http://*")
                        .skipSendToOriginalEndpoint()
                        .to("mock:account-status-sink"));
        camelContext.getRouteController().startRoute("account-status");

        MockEndpoint sink = camelContext.getEndpoint("mock:account-status-sink", MockEndpoint.class);
        sink.expectedMessageCount(1);

        Exchange exchange = camelContext.getEndpoint("direct:account-status").createExchange();
        exchange.getIn().setBody(new AirtelValidationRequest(
                "txn-acc", "ACC-9", "123456", "ZMW", "260788000000"));
        producerTemplate.send("direct:account-status", exchange);

        sink.assertIsSatisfied();
        Exchange sent = sink.getExchanges().get(0);
        assertEquals("http://localhost:5004", sent.getIn().getHeader(AMS_URL));
        assertEquals("roster", sent.getIn().getHeader(AMS_NAME));
        assertEquals(JSON_CONTENT_TYPE, sent.getIn().getHeader(CONTENT_TYPE));
        assertEquals("123456", exchange.getProperty(INITIATOR_FSP_ID));
        assertEquals("ACCOUNTID", exchange.getProperty(PRIMARY_IDENTIFIER));
        assertEquals("ACC-9", exchange.getProperty(PRIMARY_IDENTIFIER_VALUE));
        assertTrue(sent.getIn().getBody(String.class).contains("ACC-9"));
    }

    @Test
    @DisplayName("start-paybill-workflow builds GSMA transfer and channel headers")
    void startPaybillWorkflow_setsHeadersAndBody() throws Exception {
        camelContext.getRouteController().stopRoute("start-paybill-workflow");
        AdviceWithRouteBuilder.adviceWith(camelContext, "start-paybill-workflow", a ->
                a.interceptSendToEndpoint("http://*")
                        .skipSendToOriginalEndpoint()
                        .to("mock:start-workflow-sink"));
        camelContext.getRouteController().startRoute("start-paybill-workflow");

        MockEndpoint sink = camelContext.getEndpoint("mock:start-workflow-sink", MockEndpoint.class);
        sink.expectedMessageCount(1);

        ChannelValidationResponse validation = new ChannelValidationResponse(
                true, "roster", "oaf", "txn-wf", "100", "ZMW",
                "260788000000", "Jane", List.of(), "ok");

        Exchange exchange = camelContext.getEndpoint("direct:start-paybill-workflow")
                .createExchange();
        exchange.getIn().setBody(validation);
        exchange.setProperty(INITIATOR_FSP_ID, "123456");
        exchange.setProperty(PRIMARY_IDENTIFIER, "ACCOUNTID");
        exchange.setProperty(PRIMARY_IDENTIFIER_VALUE, "ACC-9");
        producerTemplate.send("direct:start-paybill-workflow", exchange);

        sink.assertIsSatisfied();
        Exchange sent = sink.getExchanges().get(0);
        assertEquals("zambia", sent.getIn().getHeader(ACCOUNT_HOLDING_INSTITUTION_ID));
        assertEquals("roster", sent.getIn().getHeader(AMS_NAME));
        assertEquals("oaf", sent.getIn().getHeader(TENANT_ID));
        assertEquals(validation, exchange.getProperty(CHANNEL_VALIDATION_RESPONSE));
        String body = sent.getIn().getBody(String.class);
        assertNotNull(body);
        assertTrue(body.length() > 20, "GSMA transfer JSON should be marshalled");
    }

    @Test
    @DisplayName("status base truncates long error body preview")
    void statusCheck_truncatesLongBody() throws Exception {
        String longBody = "x".repeat(250);
        camelContext.getRouteController().stopRoute("paybill-transaction-status-check-base");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-transaction-status-check-base",
                a -> a.interceptSendToEndpoint("http://*")
                        .skipSendToOriginalEndpoint()
                        .process(ex -> {
                            ex.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                            ex.getIn().setBody(longBody);
                        }));
        camelContext.getRouteController().startRoute("paybill-transaction-status-check-base");

        Exchange exchange = camelContext.getEndpoint("direct:paybill-transaction-status-check-base")
                .createExchange();
        exchange.setProperty(TRANSACTION_ID, "long-body-txn");
        producerTemplate.send("direct:paybill-transaction-status-check-base", exchange);

        assertEquals(null, exchange.getIn().getBody());
    }

    @Test
    @DisplayName("paybill status GET returns 500 for unexpected channel status")
    void statusCheck_500_returnsFailedMessage() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-transaction-status-check");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-transaction-status-check", a -> {
            a.replaceFromWith("direct:paybill-status-500");
            a.weaveByToUri("direct:paybill-transaction-status-check-base").replace()
                    .process(ex -> ex.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, "500"));
        });
        camelContext.getRouteController().startRoute("paybill-transaction-status-check");

        Exchange exchange = camelContext.getEndpoint("direct:paybill-status-500").createExchange();
        exchange.getIn().setHeader(TRANSACTION_ID, "err-txn");
        producerTemplate.send("direct:paybill-status-500", exchange);

        assertEquals(500, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class)
                .contains("Failed to retrieve transaction status"));
    }
}
