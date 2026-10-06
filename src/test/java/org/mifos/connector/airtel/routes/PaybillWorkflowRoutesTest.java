package org.mifos.connector.airtel.routes;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CHANNEL_VALIDATION_RESPONSE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CONFIRMATION_REQUEST_BODY;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

import java.math.BigDecimal;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.CamelRouteTestSupport;
import org.mifos.connector.airtel.dto.AirtelConfirmationRequest;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.airtel.store.PaybillStateStore;
import org.mifos.connector.common.channel.dto.TransactionStatusResponseDTO;
import org.mifos.connector.common.mojaloop.type.TransferState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaybillWorkflowRoutesTest extends CamelRouteTestSupport {

    @Autowired
    private PaybillStateStore paybillStateStore;

    @Test
    @DisplayName("paybill validation failure returns 403 error response")
    void validationFailure_returns403() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-validation-response-failure");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-validation-response-failure",
                a -> a.replaceFromWith("direct:paybill-validation-failure-test"));
        camelContext.getRouteController().startRoute("paybill-validation-response-failure");

        Exchange exchange = camelContext.getEndpoint("direct:paybill-validation-failure-test")
                .createExchange();
        exchange.getIn().setBody(new ChannelValidationResponse(
                false, "roster", "oaf", "txn-f", "100", "ZMW", "2607", "X", List.of(),
                "Account invalid"));
        producerTemplate.send("direct:paybill-validation-failure-test", exchange);

        assertEquals(403, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class).contains("Account invalid"));
    }

    @Test
    @DisplayName("paybill validation failure uses default message when response message is null")
    void validationFailure_nullMessage_usesDefault() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-validation-response-failure");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-validation-response-failure",
                a -> a.replaceFromWith("direct:paybill-validation-failure-null-msg"));
        camelContext.getRouteController().startRoute("paybill-validation-response-failure");

        Exchange exchange = camelContext
                .getEndpoint("direct:paybill-validation-failure-null-msg").createExchange();
        exchange.getIn().setBody(new ChannelValidationResponse(
                false, "roster", "oaf", "txn-f", "100", "ZMW", "2607", "X", List.of(), null));
        producerTemplate.send("direct:paybill-validation-failure-null-msg", exchange);

        assertEquals(403, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class).contains("Client validation failed"));
    }

    @Test
    @DisplayName("paybill-validation-response-success stores workflow mapping")
    void validationSuccess_storesWorkflow() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-validation-response-success");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-validation-response-success",
                a -> {
                    a.replaceFromWith("direct:paybill-validation-success-test");
                    a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                            .process(ex -> { /* body already WorkflowResponse JSON string handled below */ });
                });
        camelContext.getRouteController().startRoute("paybill-validation-response-success");

        Exchange exchange = camelContext
                .getEndpoint("direct:paybill-validation-success-test").createExchange();
        exchange.setProperty(TRANSACTION_ID, "airtel-txn-ok");
        exchange.setProperty(CHANNEL_VALIDATION_RESPONSE, new ChannelValidationResponse(
                true, "roster", "oaf", "airtel-txn-ok", "100", "ZMW",
                "260788000000", "Jane Doe", List.of(), "ok"));
        exchange.getIn().setBody(
                org.mifos.connector.airtel.dto.WorkflowResponse.class
                        .getConstructor(String.class)
                        .newInstance("wf-uuid-1"));
        // After skipping unmarshal, set body as WorkflowResponse object — the next setBody
        // processor reads WorkflowResponse from body
        producerTemplate.send("direct:paybill-validation-success-test", exchange);

        assertEquals("wf-uuid-1", paybillStateStore.getWorkflowInstance("airtel-txn-ok"));
        assertTrue(exchange.getIn().getBody(String.class).contains("Jane Doe"));
    }

    @Test
    @DisplayName("confirmation continues when status is present but not COMMITTED")
    void confirmation_nonCommittedStatus_continues() throws Exception {
        paybillStateStore.putWorkflowInstance("recv-txn", "wf-recv");

        camelContext.getRouteController().stopRoute("airtel-confirmation");
        AdviceWithRouteBuilder.adviceWith(camelContext, "airtel-confirmation", a -> {
            a.replaceFromWith("direct:airtel-confirmation-recv");
            a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                    .process(ex -> { });
            a.weaveByToUri("bean-validator:*").replace().process(ex -> { });
            a.weaveByToUri("direct:paybill-transaction-status-check-for-confirmation")
                    .replace()
                    .process(ex -> {
                        TransactionStatusResponseDTO status = new TransactionStatusResponseDTO();
                        status.setTransferState(TransferState.RECEIVED);
                        status.setTransactionId("recv-txn");
                        ex.getIn().setBody(status);
                    });
        });
        camelContext.getRouteController().startRoute("airtel-confirmation");

        Exchange exchange = camelContext.getEndpoint("direct:airtel-confirmation-recv")
                .createExchange();
        exchange.getIn().setBody(new AirtelConfirmationRequest(
                "recv-txn", BigDecimal.TEN, "ZMW", "260788000000", "ACC", "123456"));
        producerTemplate.send("direct:airtel-confirmation-recv", exchange);

        assertEquals(202, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
    }

    @Test
    @DisplayName("paybill-validation-response-success returns 500 when workflow response is null")
    void validationSuccess_nullWorkflowResponse_returns500() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-validation-response-success");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-validation-response-success",
                a -> {
                    a.replaceFromWith("direct:paybill-validation-success-null");
                    a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                            .process(ex -> { });
                });
        camelContext.getRouteController().startRoute("paybill-validation-response-success");

        Exchange exchange = camelContext
                .getEndpoint("direct:paybill-validation-success-null").createExchange();
        exchange.setProperty(TRANSACTION_ID, "null-wf-txn");
        exchange.setProperty(CHANNEL_VALIDATION_RESPONSE, new ChannelValidationResponse(
                true, "roster", "oaf", "null-wf-txn", "100", "ZMW", "2607", "X", List.of(), "ok"));
        exchange.getIn().setBody((Object) null);
        producerTemplate.send("direct:paybill-validation-success-null", exchange);

        assertEquals(500, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
    }

    @Test
    @DisplayName("paybill-validation-response-success returns 500 when workflow id blank")
    void validationSuccess_blankWorkflowId_returns500() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-validation-response-success");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-validation-response-success",
                a -> {
                    a.replaceFromWith("direct:paybill-validation-success-empty");
                    a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                            .process(ex -> { });
                });
        camelContext.getRouteController().startRoute("paybill-validation-response-success");

        Exchange exchange = camelContext
                .getEndpoint("direct:paybill-validation-success-empty").createExchange();
        exchange.setProperty(TRANSACTION_ID, "empty-wf-txn");
        exchange.setProperty(CHANNEL_VALIDATION_RESPONSE, new ChannelValidationResponse(
                true, "roster", "oaf", "empty-wf-txn", "100", "ZMW", "2607", "X", List.of(), "ok"));
        exchange.getIn().setBody(new org.mifos.connector.airtel.dto.WorkflowResponse("  "));
        producerTemplate.send("direct:paybill-validation-success-empty", exchange);

        assertEquals(500, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
    }

    @Test
    @DisplayName("paybill-validation-response-success returns 500 when workflow id is null string")
    void validationSuccess_nullWorkflowId_returns500() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-validation-response-success");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-validation-response-success",
                a -> {
                    a.replaceFromWith("direct:paybill-validation-success-blank");
                    a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                            .process(ex -> { });
                });
        camelContext.getRouteController().startRoute("paybill-validation-response-success");

        Exchange exchange = camelContext
                .getEndpoint("direct:paybill-validation-success-blank").createExchange();
        exchange.setProperty(TRANSACTION_ID, "blank-wf-txn");
        exchange.setProperty(CHANNEL_VALIDATION_RESPONSE, new ChannelValidationResponse(
                true, "roster", "oaf", "blank-wf-txn", "100", "ZMW", "2607", "X", List.of(), "ok"));
        exchange.getIn().setBody(new org.mifos.connector.airtel.dto.WorkflowResponse("null"));
        producerTemplate.send("direct:paybill-validation-success-blank", exchange);

        assertEquals(500, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
    }

    @Test
    @DisplayName("confirmation without workflow mapping returns 404 via ExceptionHandler")
    void confirmation_missingWorkflow_returns404() throws Exception {
        camelContext.getRouteController().stopRoute("airtel-confirmation");
        AdviceWithRouteBuilder.adviceWith(camelContext, "airtel-confirmation", a -> {
            a.replaceFromWith("direct:airtel-confirmation-test");
            a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                    .process(ex -> { });
            a.weaveByToUri("bean-validator:*").replace().process(ex -> { });
        });
        camelContext.getRouteController().startRoute("airtel-confirmation");

        Exchange exchange = camelContext.getEndpoint("direct:airtel-confirmation-test")
                .createExchange();
        exchange.getIn().setBody(new AirtelConfirmationRequest(
                "missing-txn", BigDecimal.TEN, "ZMW", "260788000000", "ACC", "123456"));
        producerTemplate.send("direct:airtel-confirmation-test", exchange);

        assertEquals(404, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class).contains("No workflow instance found"));
    }

    @Test
    @DisplayName("confirmation success consumes workflow and returns 202")
    void confirmation_success_returns202() throws Exception {
        paybillStateStore.putWorkflowInstance("confirm-txn", "wf-confirm-1");

        camelContext.getRouteController().stopRoute("airtel-confirmation");
        AdviceWithRouteBuilder.adviceWith(camelContext, "airtel-confirmation", a -> {
            a.replaceFromWith("direct:airtel-confirmation-ok");
            a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                    .process(ex -> { });
            a.weaveByToUri("bean-validator:*").replace().process(ex -> { });
            a.weaveByToUri("direct:paybill-transaction-status-check-for-confirmation")
                    .replace()
                    .process(ex -> ex.getIn().setBody(null));
        });
        camelContext.getRouteController().startRoute("airtel-confirmation");

        Exchange exchange = camelContext.getEndpoint("direct:airtel-confirmation-ok")
                .createExchange();
        exchange.getIn().setBody(new AirtelConfirmationRequest(
                "confirm-txn", BigDecimal.TEN, "ZMW", "260788000000", "ACC", "123456"));
        producerTemplate.send("direct:airtel-confirmation-ok", exchange);

        assertEquals(202, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class)
                .contains("Confirmation request accepted"));
        assertNull(paybillStateStore.getWorkflowInstance("confirm-txn"));
    }

    @Test
    @DisplayName("confirmation rejects already committed transaction with 409")
    void confirmation_alreadyCommitted_returns409() throws Exception {
        paybillStateStore.putWorkflowInstance("dup-txn", "wf-dup");

        camelContext.getRouteController().stopRoute("airtel-confirmation");
        AdviceWithRouteBuilder.adviceWith(camelContext, "airtel-confirmation", a -> {
            a.replaceFromWith("direct:airtel-confirmation-dup");
            a.weaveByType(org.apache.camel.model.UnmarshalDefinition.class).replace()
                    .process(ex -> { });
            a.weaveByToUri("bean-validator:*").replace().process(ex -> { });
            a.weaveByToUri("direct:paybill-transaction-status-check-for-confirmation")
                    .replace()
                    .process(ex -> {
                        TransactionStatusResponseDTO status = new TransactionStatusResponseDTO();
                        status.setTransferState(TransferState.COMMITTED);
                        status.setTransactionId("dup-txn");
                        ex.getIn().setBody(status);
                    });
        });
        camelContext.getRouteController().startRoute("airtel-confirmation");

        Exchange exchange = camelContext.getEndpoint("direct:airtel-confirmation-dup")
                .createExchange();
        exchange.getIn().setBody(new AirtelConfirmationRequest(
                "dup-txn", BigDecimal.TEN, "ZMW", "260788000000", "ACC", "123456"));
        producerTemplate.send("direct:airtel-confirmation-dup", exchange);

        assertEquals(409, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class).contains("Transaction already exists"));
    }

    @Test
    @DisplayName("paybill status GET maps committed transfer to completed message")
    void statusCheck_committed_returnsCompletedMessage() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-transaction-status-check");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-transaction-status-check", a -> {
            a.replaceFromWith("direct:paybill-status-test");
            a.weaveByToUri("direct:paybill-transaction-status-check-base").replace()
                    .process(ex -> {
                        TransactionStatusResponseDTO status = new TransactionStatusResponseDTO();
                        status.setTransferState(TransferState.COMMITTED);
                        status.setTransactionId("status-txn");
                        ex.getIn().setHeader(HTTP_RESPONSE_CODE, "200");
                        ex.getIn().setBody(status);
                    });
        });
        camelContext.getRouteController().startRoute("paybill-transaction-status-check");

        Exchange exchange = camelContext.getEndpoint("direct:paybill-status-test").createExchange();
        exchange.getIn().setHeader(TRANSACTION_ID, "status-txn");
        producerTemplate.send("direct:paybill-status-test", exchange);

        assertTrue(exchange.getIn().getBody(String.class).contains("Transaction completed"));
    }

    @Test
    @DisplayName("paybill status GET maps non-committed transfer to not completed")
    void statusCheck_received_returnsNotCompleted() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-transaction-status-check");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-transaction-status-check", a -> {
            a.replaceFromWith("direct:paybill-status-received");
            a.weaveByToUri("direct:paybill-transaction-status-check-base").replace()
                    .process(ex -> {
                        TransactionStatusResponseDTO status = new TransactionStatusResponseDTO();
                        status.setTransferState(TransferState.RECEIVED);
                        status.setTransactionId("status-txn-2");
                        ex.getIn().setHeader(HTTP_RESPONSE_CODE, "200");
                        ex.getIn().setBody(status);
                    });
        });
        camelContext.getRouteController().startRoute("paybill-transaction-status-check");

        Exchange exchange = camelContext.getEndpoint("direct:paybill-status-received")
                .createExchange();
        exchange.getIn().setHeader(TRANSACTION_ID, "status-txn-2");
        producerTemplate.send("direct:paybill-status-received", exchange);

        assertTrue(exchange.getIn().getBody(String.class).contains("Transaction not completed"));
    }

    @Test
    @DisplayName("paybill status GET returns 404 error body for missing transaction")
    void statusCheck_404_returnsNotFound() throws Exception {
        camelContext.getRouteController().stopRoute("paybill-transaction-status-check");
        AdviceWithRouteBuilder.adviceWith(camelContext, "paybill-transaction-status-check", a -> {
            a.replaceFromWith("direct:paybill-status-404");
            a.weaveByToUri("direct:paybill-transaction-status-check-base").replace()
                    .process(ex -> ex.getIn().setHeader(HTTP_RESPONSE_CODE, "404"));
        });
        camelContext.getRouteController().startRoute("paybill-transaction-status-check");

        Exchange exchange = camelContext.getEndpoint("direct:paybill-status-404").createExchange();
        exchange.getIn().setHeader(TRANSACTION_ID, "missing");
        producerTemplate.send("direct:paybill-status-404", exchange);

        assertEquals(404, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        assertTrue(exchange.getIn().getBody(String.class).contains("Transaction not found"));
    }

    @Test
    @DisplayName("status check for confirmation swallows channel errors and continues")
    void statusCheckForConfirmation_swallowsErrors() throws Exception {
        camelContext.getRouteController()
                .stopRoute("paybill-transaction-status-check-for-confirmation");
        AdviceWithRouteBuilder.adviceWith(camelContext,
                "paybill-transaction-status-check-for-confirmation", a -> {
                    a.replaceFromWith("direct:status-for-confirmation-test");
                    a.weaveByToUri("direct:paybill-transaction-status-check-base")
                            .replace()
                            .process(ex -> {
                                throw new RuntimeException("channel down");
                            });
                });
        camelContext.getRouteController()
                .startRoute("paybill-transaction-status-check-for-confirmation");

        Exchange exchange = camelContext.getEndpoint("direct:status-for-confirmation-test")
                .createExchange();
        exchange.setProperty(TRANSACTION_ID, "txn-x");
        exchange.setProperty(CONFIRMATION_REQUEST_BODY, new AirtelConfirmationRequest(
                "txn-x", BigDecimal.ONE, "ZMW", "2607", "ACC", "123456"));
        producerTemplate.send("direct:status-for-confirmation-test", exchange);

        assertNull(exchange.getIn().getBody());
    }
}
