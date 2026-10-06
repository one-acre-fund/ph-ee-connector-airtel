package org.mifos.connector.airtel.camel.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_RETRY_EXCEEDED;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_TRANSACTION_PENDING;
import static org.mifos.connector.airtel.camel.config.CamelProperties.LAST_RESPONSE_BODY;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AIRTEL_MONEY_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CALLBACK;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CALLBACK_RECEIVED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_CODE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_DESCRIPTION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_INFORMATION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.GET_TRANSACTION_STATUS_RESPONSE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.GET_TRANSACTION_STATUS_RESPONSE_CODE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TIMER;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSFER_CREATE_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSFER_MESSAGE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CollectionResponseProcessorTest {

    private ZeebeClient zeebeClient;
    private CollectionResponseProcessor processor;
    private Exchange exchange;

    private PublishMessageCommandStep1 publishStep1;
    private PublishMessageCommandStep1.PublishMessageCommandStep2 publishStep2;
    private PublishMessageCommandStep1.PublishMessageCommandStep3 publishStep3;
    private SetVariablesCommandStep1 setVariablesStep1;

    @BeforeEach
    void setUp() {
        zeebeClient = mock(ZeebeClient.class);
        processor = new CollectionResponseProcessor(zeebeClient, new ObjectMapper());
        ReflectionTestUtils.setField(processor, "timeToLive", 30000);
        exchange = new DefaultExchange(new DefaultCamelContext());

        publishStep1 = mock(PublishMessageCommandStep1.class);
        publishStep2 = mock(PublishMessageCommandStep1.PublishMessageCommandStep2.class);
        publishStep3 = mock(PublishMessageCommandStep1.PublishMessageCommandStep3.class);
        @SuppressWarnings("unchecked")
        ZeebeFuture<PublishMessageResponse> publishFuture = mock(ZeebeFuture.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(publishStep1);
        when(publishStep1.messageName(anyString())).thenReturn(publishStep2);
        when(publishStep2.correlationKey(anyString())).thenReturn(publishStep3);
        when(publishStep3.timeToLive(any(Duration.class))).thenReturn(publishStep3);
        when(publishStep3.variables(anyMap())).thenReturn(publishStep3);
        when(publishStep3.send()).thenReturn(publishFuture);
        when(publishFuture.join()).thenReturn(null);

        setVariablesStep1 = mock(SetVariablesCommandStep1.class);
        SetVariablesCommandStep1.SetVariablesCommandStep2 setVariablesStep2 =
                mock(SetVariablesCommandStep1.SetVariablesCommandStep2.class);
        @SuppressWarnings("unchecked")
        ZeebeFuture<SetVariablesResponse> setFuture = mock(ZeebeFuture.class);
        when(zeebeClient.newSetVariablesCommand(anyLong())).thenReturn(setVariablesStep1);
        when(setVariablesStep1.variables(anyMap())).thenReturn(setVariablesStep2);
        when(setVariablesStep2.send()).thenReturn(setFuture);
        when(setFuture.join()).thenReturn(null);
    }

    @Test
    @DisplayName("Pending transaction updates Zeebe variables and does not publish message")
    void pendingTransaction_updatesTimerOnly() throws Exception {
        exchange.setProperty(IS_TRANSACTION_PENDING, true);
        exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 1);
        exchange.setProperty(TIMER, "PT45S");
        exchange.setProperty(ZEEBE_ELEMENT_INSTANCE_KEY, 99L);
        exchange.setProperty(LAST_RESPONSE_BODY, "{\"status\":\"pending\"}");
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);

        processor.process(exchange);

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(zeebeClient).newSetVariablesCommand(99L);
        verify(setVariablesStep1).variables(varsCaptor.capture());
        assertEquals("PT90S", varsCaptor.getValue().get(TIMER));
        verify(zeebeClient, never()).newPublishMessageCommand();
    }

    @Test
    @DisplayName("Successful outcome publishes message with airtel money id and callback")
    void success_publishesWithCallbackFields() throws Exception {
        exchange.setProperty(TRANSACTION_ID, "txn-1");
        exchange.setProperty(TRANSACTION_FAILED, false);
        exchange.setProperty(AIRTEL_MONEY_ID, "AM-1");
        exchange.setProperty(CALLBACK, "callback-body");
        exchange.setProperty(CALLBACK_RECEIVED, true);

        processor.process(exchange);

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publishStep1).messageName(TRANSFER_MESSAGE);
        verify(publishStep2).correlationKey("txn-1");
        verify(publishStep3).variables(varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertFalse((Boolean) vars.get(TRANSACTION_FAILED));
        assertFalse((Boolean) vars.get(TRANSFER_CREATE_FAILED));
        assertEquals("AM-1", vars.get(AIRTEL_MONEY_ID));
        assertEquals("callback-body", vars.get(CALLBACK));
        assertEquals(true, vars.get(CALLBACK_RECEIVED));
    }

    @Test
    @DisplayName("Failed outcome includes error fields when retries not exceeded")
    void failure_includesErrorFields() throws Exception {
        exchange.setProperty(TRANSACTION_ID, "txn-fail");
        exchange.setProperty(TRANSACTION_FAILED, true);
        exchange.setProperty(ERROR_INFORMATION, "err-info");
        exchange.setProperty(ERROR_CODE, "E1");
        exchange.setProperty(ERROR_DESCRIPTION, "desc");

        processor.process(exchange);

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publishStep3).variables(varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertTrue((Boolean) vars.get(TRANSACTION_FAILED));
        assertTrue((Boolean) vars.get(TRANSFER_CREATE_FAILED));
        assertEquals("err-info", vars.get(ERROR_INFORMATION));
        assertEquals("E1", vars.get(ERROR_CODE));
        assertEquals("desc", vars.get(ERROR_DESCRIPTION));
    }

    @Test
    @DisplayName("Failed outcome omits error fields when retries exceeded")
    void failure_omitsErrorFieldsWhenRetryExceeded() throws Exception {
        exchange.setProperty(TRANSACTION_ID, "txn-fail");
        exchange.setProperty(TRANSACTION_FAILED, true);
        exchange.setProperty(IS_RETRY_EXCEEDED, true);
        exchange.setProperty(ERROR_INFORMATION, "err-info");

        processor.process(exchange);

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publishStep3).variables(varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertTrue((Boolean) vars.get(TRANSACTION_FAILED));
        assertFalse(vars.containsKey(ERROR_INFORMATION));
    }

    @Test
    @DisplayName("Status response body falls back to HTTP response text and exception status")
    void statusVariables_useHttpTextAndExceptionStatus() throws Exception {
        exchange.setProperty(TRANSACTION_ID, "txn-status");
        exchange.setProperty(TRANSACTION_FAILED, false);
        exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 2);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_TEXT, "status-text");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT,
                new HttpOperationFailedException("http://x", 503, "Unavailable", null,
                        Collections.emptyMap(), null));

        processor.process(exchange);

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publishStep3).variables(varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertEquals("status-text", vars.get(GET_TRANSACTION_STATUS_RESPONSE));
        assertEquals(503, vars.get(GET_TRANSACTION_STATUS_RESPONSE_CODE));
    }
}
