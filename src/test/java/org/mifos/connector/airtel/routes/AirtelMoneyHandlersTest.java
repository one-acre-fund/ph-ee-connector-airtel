package org.mifos.connector.airtel.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_RETRY_EXCEEDED;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_TRANSACTION_PENDING;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AIRTEL_MONEY_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CALLBACK_RECEIVED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_CODE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_DESCRIPTION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TIMER;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.CamelRouteTestSupport;
import org.mifos.connector.airtel.camel.routes.AirtelMoneyRouteBuilder;
import org.mifos.connector.airtel.dto.CallbackDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

class AirtelMoneyHandlersTest extends CamelRouteTestSupport {

    @Autowired
    private AirtelMoneyRouteBuilder airtelMoneyRouteBuilder;

    private static final String SUCCESS_COLLECTION_BODY = """
            {
              "data": {
                "transaction": {
                  "id": "txn-1",
                  "message": "ok",
                  "status": "TS",
                  "airtel_money_id": "AM-1"
                }
              },
              "status": {
                "code": "200",
                "message": "ok",
                "response_code": "DP00800001006",
                "success": true
              }
            }
            """;

    private static final String FAILED_COLLECTION_BODY = """
            {
              "data": {
                "transaction": {
                  "id": "txn-1",
                  "message": "failed",
                  "status": "TF",
                  "airtel_money_id": "AM-1"
                }
              },
              "status": {
                "code": "400",
                "message": "failed",
                "response_code": "ERR",
                "success": false
              }
            }
            """;

    private static final String STATUS_TS_BODY = """
            {
              "data": {
                "transaction": {
                  "id": "txn-1",
                  "message": "Success",
                  "status": "TS",
                  "airtel_money_id": "AM-99"
                }
              },
              "status": {
                "code": "200",
                "message": "ok",
                "response_code": "OK",
                "success": true
              }
            }
            """;

    private static final String STATUS_TF_BODY = """
            {
              "data": {
                "transaction": {
                  "id": "txn-1",
                  "message": "Insufficient funds",
                  "status": "TF",
                  "airtel_money_id": "AM-99"
                }
              },
              "status": {
                "code": "200",
                "message": "ok",
                "response_code": "OK",
                "success": true
              }
            }
            """;

    private static final String STATUS_PENDING_BODY = """
            {
              "data": {
                "transaction": {
                  "id": "txn-1",
                  "message": "Pending",
                  "status": "TIP",
                  "airtel_money_id": null
                }
              },
              "status": {
                "code": "200",
                "message": "ok",
                "response_code": "OK",
                "success": true
              }
            }
            """;

    private static final String STATUS_API_FAIL_BODY = """
            {
              "data": null,
              "status": {
                "code": "400",
                "message": "Bad request",
                "response_code": "ERR2",
                "success": false
              }
            }
            """;

    @Test
    @DisplayName("collection-response-handler marks success on HTTP 200 with success=true")
    void collectionResponseHandler_success() {
        Exchange exchange = sendHandler("collection-response-handler", SUCCESS_COLLECTION_BODY, 200);
        assertFalse((Boolean) exchange.getProperty(TRANSACTION_FAILED));
    }

    @Test
    @DisplayName("collection-response-handler marks failure on HTTP 200 with success=false")
    void collectionResponseHandler_businessFailure() {
        Exchange exchange = sendHandler("collection-response-handler", FAILED_COLLECTION_BODY, 200);
        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("ERR", exchange.getProperty(ERROR_CODE));
        assertEquals("failed", exchange.getProperty(ERROR_DESCRIPTION));
    }

    @Test
    @DisplayName("collection-response-handler marks failure on non-200")
    void collectionResponseHandler_non200() {
        Exchange exchange = sendHandler("collection-response-handler", "error", 500);
        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals(500, exchange.getProperty(ERROR_CODE));
    }

    @Test
    @DisplayName("transaction-status-response-handler handles TS success")
    void statusHandler_tsSuccess() {
        Exchange exchange = sendStatusHandler(STATUS_TS_BODY, 200);
        assertFalse((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("AM-99", exchange.getProperty(AIRTEL_MONEY_ID));
    }

    @Test
    @DisplayName("transaction-status-response-handler handles TF failure")
    void statusHandler_tfFailure() {
        Exchange exchange = sendStatusHandler(STATUS_TF_BODY, 200);
        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("Insufficient funds", exchange.getProperty(ERROR_DESCRIPTION));
    }

    @Test
    @DisplayName("transaction-status-response-handler marks pending for intermediate status")
    void statusHandler_pending() {
        Exchange exchange = sendStatusHandler(STATUS_PENDING_BODY, 200);
        assertTrue((Boolean) exchange.getProperty(IS_TRANSACTION_PENDING));
    }

    @Test
    @DisplayName("transaction-status-response-handler handles API-level failure")
    void statusHandler_apiFailure() {
        Exchange exchange = sendStatusHandler(STATUS_API_FAIL_BODY, 200);
        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("Bad request", exchange.getProperty(ERROR_DESCRIPTION));
    }

    @Test
    @DisplayName("transaction-status-response-handler handles non-200")
    void statusHandler_non200() {
        Exchange exchange = sendStatusHandler("boom", 502);
        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
    }

    @Test
    @DisplayName("get-transaction-status-base marks retry exceeded when count too high")
    void getTransactionStatus_retryExceeded() throws Exception {
        camelContext.getRouteController().stopRoute("get-transaction-status-base");
        AdviceWithRouteBuilder.adviceWith(camelContext, "get-transaction-status-base", a ->
                a.weaveByToUri("direct:get-access-token").replace().to("mock:unused-token"));
        camelContext.getRouteController().startRoute("get-transaction-status-base");

        Exchange exchange = camelContext.getEndpoint("direct:get-transaction-status-base")
                .createExchange();
        exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 99);
        exchange.setProperty(TRANSACTION_ID, "retry-txn");
        exchange.setProperty(ZEEBE_ELEMENT_INSTANCE_KEY, 1L);
        exchange.setProperty(TIMER, "PT45S");
        producerTemplate.send("direct:get-transaction-status-base", exchange);

        assertTrue((Boolean) exchange.getProperty(IS_RETRY_EXCEEDED));
        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("RETRY_EXCEEDED", exchange.getProperty(ERROR_CODE));
    }

    @Test
    @DisplayName("callback-handler strips transaction prefix and marks TS success")
    void callbackHandler_successStripsPrefix() {
        ReflectionTestUtils.setField(airtelMoneyRouteBuilder, "transactionIdPrefix", "oaf-");

        CallbackDto.Transaction txn = new CallbackDto.Transaction();
        txn.setId("oaf-cb-1");
        txn.setStatusCode("TS");
        txn.setAirtelMoneyId("AM-CB");
        txn.setMessage("ok");
        CallbackDto callback = new CallbackDto();
        callback.setTransaction(txn);

        Exchange exchange = camelContext.getEndpoint("direct:callback-handler").createExchange();
        exchange.getIn().setBody(callback);
        producerTemplate.send("direct:callback-handler", exchange);

        assertEquals("cb-1", exchange.getProperty(TRANSACTION_ID));
        assertEquals(true, exchange.getProperty(CALLBACK_RECEIVED));
        assertFalse((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("AM-CB", exchange.getProperty(AIRTEL_MONEY_ID));
    }

    @Test
    @DisplayName("callback-handler marks TF as failed")
    void callbackHandler_failure() {
        CallbackDto.Transaction txn = new CallbackDto.Transaction();
        txn.setId("cb-fail");
        txn.setStatusCode("TF");
        txn.setMessage("declined");
        txn.setAirtelMoneyId("AM-X");
        CallbackDto callback = new CallbackDto();
        callback.setTransaction(txn);

        Exchange exchange = camelContext.getEndpoint("direct:callback-handler").createExchange();
        exchange.getIn().setBody(callback);
        producerTemplate.send("direct:callback-handler", exchange);

        assertTrue((Boolean) exchange.getProperty(TRANSACTION_FAILED));
        assertEquals("declined", exchange.getProperty(ERROR_DESCRIPTION));
    }

    @Test
    @DisplayName("callback-handler leaves intermediate state without TRANSACTION_FAILED")
    void callbackHandler_intermediate() {
        CallbackDto.Transaction txn = new CallbackDto.Transaction();
        txn.setId("cb-pending");
        txn.setStatusCode("TIP");
        txn.setAirtelMoneyId("AM-P");
        CallbackDto callback = new CallbackDto();
        callback.setTransaction(txn);

        Exchange exchange = camelContext.getEndpoint("direct:callback-handler").createExchange();
        exchange.getIn().setBody(callback);
        producerTemplate.send("direct:callback-handler", exchange);

        assertNull(exchange.getProperty(TRANSACTION_FAILED));
    }

    private Exchange sendHandler(String routeId, String body, int httpCode) {
        Exchange exchange = camelContext.getEndpoint("direct:" + routeId).createExchange();
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, httpCode);
        producerTemplate.send("direct:" + routeId, exchange);
        return exchange;
    }

    private Exchange sendStatusHandler(String body, int httpCode) {
        Exchange exchange = camelContext.getEndpoint("direct:transaction-status-response-handler")
                .createExchange();
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, httpCode);
        exchange.setProperty(TRANSACTION_ID, "status-txn");
        exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 1);
        exchange.setProperty(ZEEBE_ELEMENT_INSTANCE_KEY, 42L);
        exchange.setProperty(TIMER, "PT45S");
        producerTemplate.send("direct:transaction-status-response-handler", exchange);
        return exchange;
    }
}
