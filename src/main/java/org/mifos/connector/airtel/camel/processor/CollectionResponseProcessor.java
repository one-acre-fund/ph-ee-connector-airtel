package org.mifos.connector.airtel.camel.processor;

import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_RETRY_EXCEEDED;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_TRANSACTION_PENDING;
import static org.mifos.connector.airtel.camel.config.CamelProperties.LAST_RESPONSE_BODY;
import static org.mifos.connector.airtel.util.ZeebeUtils.getNextTimer;
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
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSFER_RESPONSE_CREATE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.util.json.JsonObject;
import org.mifos.connector.airtel.util.ZeebeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Camel processor for collection response received from Airtel.
 */
@Component
public class CollectionResponseProcessor implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(CollectionResponseProcessor.class);
    private final ZeebeClient zeebeClient;
    private final ObjectMapper objectMapper;
    @Value("${zeebe.client.ttl:30000}")
    private int timeToLive;

    public CollectionResponseProcessor(ZeebeClient zeebeClient, ObjectMapper objectMapper) {
        this.zeebeClient = zeebeClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws JsonProcessingException {
        Map<String, Object> variables = new HashMap<>();
        Object updatedRetryCount = exchange.getProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT);
        setTransactionStatusResponseVariables(exchange, variables, updatedRetryCount);

        // Update timer (when next to attempt getting the transaction status) if the transaction
        // is pending and the number of retries hasn't been exceeded.
        Boolean isRetryExceeded = exchange.getProperty(IS_RETRY_EXCEEDED, Boolean.class);
        Boolean isTransactionPending = exchange.getProperty(IS_TRANSACTION_PENDING, Boolean.class);
        if (Boolean.TRUE.equals(isTransactionPending)
            && (isRetryExceeded == null || !isRetryExceeded)) {
            String newTimer = getNextTimer(exchange.getProperty(TIMER, String.class));
            logger.info("Updating retry count to {}", updatedRetryCount);
            logger.info("Updating timer value to {}", newTimer);
            variables.put(TIMER, newTimer);
            Long elementInstanceKey = (Long) exchange.getProperty(ZEEBE_ELEMENT_INSTANCE_KEY);
            zeebeClient.newSetVariablesCommand(elementInstanceKey)
                .variables(variables)
                .send()
                .join();
            return;
        }

        handleTransactionOutcome(exchange, variables, isRetryExceeded);
        variables.put(TRANSFER_RESPONSE_CREATE, ZeebeUtils.getTransferResponseCreateJson());

        String correlationId = exchange.getProperty(TRANSACTION_ID, String.class);
        logger.info("Publishing transaction message variables: {}", variables);
        zeebeClient.newPublishMessageCommand()
            .messageName(TRANSFER_MESSAGE)
            .correlationKey(correlationId)
            .timeToLive(Duration.ofMillis(timeToLive))
            .variables(variables)
            .send()
            .join();
    }

    /**
     * Sets Zeebe variables that'd hold the transaction response status and body returned by Airtel
     * transaction status API.
     *
     * @param exchange          {@link Exchange}
     * @param variables         key/value pair of Zeebe variables.
     * @param updatedRetryCount number of times the Airtel transaction status API has been called.
     */
    private void setTransactionStatusResponseVariables(Exchange exchange,
                                                       Map<String, Object> variables,
                                                       Object updatedRetryCount) {
        if (updatedRetryCount != null) {
            variables.put(SERVER_TRANSACTION_STATUS_RETRY_COUNT, updatedRetryCount);
            Boolean isRetryExceeded = (Boolean) exchange.getProperty(IS_RETRY_EXCEEDED);
            if (isRetryExceeded == null || !isRetryExceeded) {
                String body = exchange.getProperty(LAST_RESPONSE_BODY, String.class);
                Object statusCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE);
                if (body == null) {
                    body = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_TEXT, String.class);
                }
                if (statusCode == null) {
                    Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (e instanceof HttpOperationFailedException httpOperationFailedException) {
                        statusCode = httpOperationFailedException.getStatusCode();
                    }
                }
                variables.put(GET_TRANSACTION_STATUS_RESPONSE, body);
                variables.put(GET_TRANSACTION_STATUS_RESPONSE_CODE, statusCode);
            }
        }
    }

    /**
     * Checks if the transaction failed or succeeded, and sets the relevant zeebe variables.
     *
     * @param exchange        {@link Exchange}
     * @param variables       key/value pair of Zeebe variables.
     * @param isRetryExceeded boolean flag that tells if the number of allowed retries has been
     *                        exceeded or not.
     */
    private void handleTransactionOutcome(Exchange exchange, Map<String, Object> variables,
                                          Boolean isRetryExceeded) {
        Boolean transactionFailed = exchange.getProperty(TRANSACTION_FAILED, Boolean.class);
        if (Boolean.TRUE.equals(transactionFailed)) {
            variables.put(TRANSACTION_FAILED, true);
            variables.put(TRANSFER_CREATE_FAILED, true);
            if (isRetryExceeded == null || !isRetryExceeded) {
                variables.put(ERROR_INFORMATION,
                    exchange.getProperty(ERROR_INFORMATION, String.class));
                variables.put(ERROR_CODE, exchange.getProperty(ERROR_CODE, String.class));
                variables.put(ERROR_DESCRIPTION,
                    exchange.getProperty(ERROR_DESCRIPTION, String.class));
            }
        } else {
            variables.put(TRANSACTION_FAILED, false);
            variables.put(TRANSFER_CREATE_FAILED, false);
            String airtelMoneyId = exchange.getProperty(AIRTEL_MONEY_ID, String.class);
            if (airtelMoneyId != null) {
                variables.put(AIRTEL_MONEY_ID, airtelMoneyId);
            }
            String callback = exchange.getProperty(CALLBACK, String.class);
            if (callback != null) {
                variables.put(CALLBACK, callback);
                variables.put(CALLBACK_RECEIVED, exchange.getProperty(CALLBACK_RECEIVED));
            }
        }
    }
}
