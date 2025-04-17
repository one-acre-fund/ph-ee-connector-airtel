package org.mifos.connector.airtel.camel.routes;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.ACCOUNT_HOLDING_INSTITUTION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.AMS_NAME;
import static org.mifos.connector.airtel.camel.config.CamelProperties.AMS_URL;
import static org.mifos.connector.airtel.camel.config.CamelProperties.BRIDGE_ENDPOINT_QUERY_PARAM;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CHANNEL_VALIDATION_RESPONSE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CONFIRMATION_REQUEST_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CORRELATION_ID_HEADER;
import static org.mifos.connector.airtel.camel.config.CamelProperties.JSON_CONTENT_TYPE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PRIMARY_IDENTIFIER;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PRIMARY_IDENTIFIER_VALUE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CHANNEL_REQUEST;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CONFIRMATION_RECEIVED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.EXTERNAL_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INITIATOR_FSP_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSFER_CREATE_FAILED;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.builder.RouteBuilder;
import org.mifos.connector.airtel.dto.AirtelConfirmationRequest;
import org.mifos.connector.airtel.dto.AirtelConfirmationResponse;
import org.mifos.connector.airtel.dto.AirtelValidationRequest;
import org.mifos.connector.airtel.dto.AirtelValidationResponse;
import org.mifos.connector.airtel.dto.ChannelConfirmationRequest;
import org.mifos.connector.airtel.dto.ChannelValidationRequest;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.airtel.dto.ErrorResponse;
import org.mifos.connector.airtel.dto.PaybillProps;
import org.mifos.connector.airtel.dto.TransactionStatusResponse;
import org.mifos.connector.airtel.dto.WorkflowResponse;
import org.mifos.connector.airtel.exception.TransactionAlreadyExistsException;
import org.mifos.connector.airtel.exception.WorkflowNotFoundException;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.mifos.connector.common.channel.dto.TransactionStatusResponseDTO;
import org.mifos.connector.common.mojaloop.type.TransferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Routes for the paybill workflow.
 */
@Component
public class PaybillRouteBuilder extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(PaybillRouteBuilder.class);
    public static final Map<String, String> workflowInstanceStore = new HashMap<>();
    private final ZeebeClient zeebeClient;
    private final PaybillProps paybillProps;
    private final String channelUrl;

    /**
     * Creates a new {@link PaybillRouteBuilder} object.
     *
     * @param zeebeClient  {@link ZeebeClient}
     * @param paybillProps {@link PaybillProps}
     * @param channelUrl   the URL to be used in communicating with the channel connector
     */
    public PaybillRouteBuilder(ZeebeClient zeebeClient, PaybillProps paybillProps,
                               @Value("${channel.host}") String channelUrl) {
        this.zeebeClient = zeebeClient;
        this.paybillProps = paybillProps;
        this.channelUrl = channelUrl;
    }

    @Override
    public void configure() {

        from("rest:POST:/paybill/validation")
            .id("airtel-validation")
            .unmarshal().json(AirtelValidationRequest.class)
            .log("Received airtel validation request with transaction id: ${body.transactionId}, "
                + "body: ${body} ")
            .to("bean-validator:validation-request-validator")
            .setProperty(TRANSACTION_ID, simple("${body.transactionId}"))
            .to("direct:account-status")
            .log("Response from account status check for transaction id "
                + "${exchangeProperty.transactionId}: ${body}")
            .unmarshal().json(ChannelValidationResponse.class)
            .choice()
                .when(simple("${body.reconciled} == 'true'"))
                    .to("direct:start-paybill-workflow")
                    .to("direct:paybill-validation-response-success")
                .otherwise()
                    .to("direct:paybill-validation-response-failure")
            .end();

        from("rest:POST:/paybill/confirmation")
            .id("airtel-confirmation")
            .unmarshal().json(AirtelConfirmationRequest.class)
            .log("Received airtel confirmation request with transaction id: ${body.transactionId},"
                + " body: ${body} ")
            .to("bean-validator:confirmation-request-validator")
            .setProperty(CONFIRMATION_REQUEST_BODY, simple("${body}"))
            .process(exchange -> {
                AirtelConfirmationRequest request = exchange.getProperty(CONFIRMATION_REQUEST_BODY,
                    AirtelConfirmationRequest.class);
                String workflowTransactionId = workflowInstanceStore.get(request.transactionId());
                if (workflowTransactionId == null) {
                    throw new WorkflowNotFoundException("No workflow instance found for "
                        + "transaction id " + request.transactionId()
                        + ". The transaction may not have been validated.");
                }
                workflowInstanceStore.remove(request.transactionId());
                exchange.setProperty(CORRELATION_ID, workflowTransactionId);
            })
            .setProperty(TRANSACTION_ID, simple("${body.transactionId}"))
            .to("direct:paybill-transaction-status-check-base")
            .process(exchange -> {
                // Ensure the transaction doesn't already exist
                TransactionStatusResponseDTO transactionStatusResponse = exchange.getIn()
                    .getBody(TransactionStatusResponseDTO.class);
                if (transactionStatusResponse != null && TransferState.COMMITTED
                    .equals(transactionStatusResponse.getTransferState())) {
                    throw new TransactionAlreadyExistsException("Transaction already exists");
                }

                AirtelConfirmationRequest request = exchange.getProperty(CONFIRMATION_REQUEST_BODY,
                    AirtelConfirmationRequest.class);
                PaybillProps.AmsProps amsProps = paybillProps
                    .getAmsProps(request.businessShortCode());
                ChannelConfirmationRequest confirmationRequest = ChannelConfirmationRequest
                    .fromPaybillConfirmation(request, amsProps);
                String workflowTransactionId = exchange.getProperty(CORRELATION_ID, String.class);
                Map<String, Object> variables = new HashMap<>();
                variables.put(CONFIRMATION_RECEIVED, true);
                variables.put(CHANNEL_REQUEST, confirmationRequest.toString());
                variables.put(amsProps.getIdentifier(), request.accountNumber());
                variables.put(TRANSACTION_ID, request.transactionId());
                variables.put(CORRELATION_ID, workflowTransactionId);
                variables.put(EXTERNAL_ID, request.transactionId());
                variables.put(TRANSFER_CREATE_FAILED, false);
                variables.put("phoneNumber", request.msisdn());
                variables.put("amount", request.amount());
                zeebeClient.newPublishMessageCommand()
                    .messageName("pendingAirtelConfirmation")
                    .correlationKey(workflowTransactionId)
                    .timeToLive(Duration.ofMillis(300))
                    .variables(variables)
                    .send();
            })
            .setHeader(HTTP_RESPONSE_CODE, constant(202))
            .setBody(constant(new AirtelConfirmationResponse("Confirmation request accepted")))
            .marshal().json();

        from("rest:GET:/paybill/status/{transactionId}")
            .id("paybill-transaction-status-check")
            .setProperty(TRANSACTION_ID, header(TRANSACTION_ID))
            .to("direct:paybill-transaction-status-check-base")
            .choice()
                .when(header(HTTP_RESPONSE_CODE).isEqualTo("200"))
                    .setBody(exchange -> {
                        TransactionStatusResponseDTO response = exchange.getIn()
                            .getBody(TransactionStatusResponseDTO.class);
                        String message = TransferState.COMMITTED.equals(response.getTransferState())
                            ? "Transaction completed"
                            : "Transaction not completed";
                        return new TransactionStatusResponse(message, response.getTransferState(),
                            response.getTransactionId());
                    })
                .when(header(HTTP_RESPONSE_CODE).isEqualTo("404"))
                    .setHeader(HTTP_RESPONSE_CODE, constant(404))
                    .setBody(constant(new ErrorResponse("Transaction not found", null)))
                .otherwise()
                    .setHeader(HTTP_RESPONSE_CODE, constant(500))
                    .setBody(constant(new ErrorResponse(
                        "Failed to retrieve transaction status", null
                        ))
                    )
            .end()
            .marshal().json();

        from("direct:paybill-transaction-status-check-base")
            .id("paybill-transaction-status-check-base")
            .process(exchange -> {
                exchange.getIn().removeHeaders("*");
                exchange.getIn().setBody(null);
                exchange.getIn().setHeader(PLATFORM_TENANT_ID,
                    paybillProps.getAccountHoldingInstitutionId());
                exchange.getIn().setHeader("requestType", "transfers");
            })
            .toD(channelUrl + "/channel/transfer/${header.transactionId}"
                + BRIDGE_ENDPOINT_QUERY_PARAM)
            .log("Received status response for transaction ${header.transactionId}: ${body}")
            .unmarshal().json(TransactionStatusResponseDTO.class);

        from("direct:account-status")
            .id("account-status")
            .setBody(exchange -> {
                AirtelValidationRequest request = exchange.getIn()
                    .getBody(AirtelValidationRequest.class);
                PaybillProps.AmsProps amsProps = paybillProps
                    .getAmsProps(request.businessShortCode());
                exchange.getIn().removeHeaders("*");
                exchange.getIn().setHeader(CONTENT_TYPE, JSON_CONTENT_TYPE);
                exchange.getIn().setHeader(AMS_URL, amsProps.getAmsUrl());
                exchange.getIn().setHeader(AMS_NAME, amsProps.getAmsName());
                exchange.getIn().setHeader(ACCOUNT_HOLDING_INSTITUTION_ID,
                    paybillProps.getAccountHoldingInstitutionId());
                exchange.setProperty(INITIATOR_FSP_ID, amsProps.getBusinessShortCode());
                exchange.setProperty(PRIMARY_IDENTIFIER, amsProps.getIdentifier());
                exchange.setProperty(PRIMARY_IDENTIFIER_VALUE, request.accountNumber());
                return ChannelValidationRequest.fromPaybillValidation(request, amsProps);
            })
            .marshal().json()
            .toD(channelUrl
                + "/accounts/validate/${header.primaryIdentifier}/${header.primaryIdentifierValue}"
                + BRIDGE_ENDPOINT_QUERY_PARAM);

        from("direct:start-paybill-workflow")
            .id("start-paybill-workflow")
            .log("Starting paybill workflow for transaction id ${exchangeProperty.transactionId}")
            .setBody(exchange -> {
                ChannelValidationResponse validationResponse = exchange.getIn()
                    .getBody(ChannelValidationResponse.class);
                exchange.getIn().removeHeaders("*");
                exchange.getIn().setHeader(ACCOUNT_HOLDING_INSTITUTION_ID,
                    validationResponse.accountHoldingInstitutionId());
                exchange.getIn().setHeader(AMS_NAME, validationResponse.amsName());
                exchange.getIn().setHeader(TENANT_ID,
                    validationResponse.accountHoldingInstitutionId());
                exchange.getIn().setHeader(CORRELATION_ID_HEADER,
                    validationResponse.transactionId());
                exchange.getIn().setHeader(CONTENT_TYPE, JSON_CONTENT_TYPE);
                exchange.setProperty(CHANNEL_VALIDATION_RESPONSE, validationResponse);
                String businessShortCode = exchange.getProperty(INITIATOR_FSP_ID, String.class);
                String primaryIdentifier = exchange.getProperty(PRIMARY_IDENTIFIER, String.class);
                String primaryIdentifierValue = exchange.getProperty(PRIMARY_IDENTIFIER_VALUE,
                    String.class);
                String timer = paybillProps.getTimer();
                return AirtelUtils.createGsmaTransferRequest(validationResponse,
                    businessShortCode, primaryIdentifier, primaryIdentifierValue, timer);
            })
            .marshal().json()
            .toD(channelUrl + "/channel/gsma/transaction" + BRIDGE_ENDPOINT_QUERY_PARAM)
            .log("Paybill workflow response from channel for transaction id "
                + "${exchangeProperty.transactionId}: ${body}");

        from("direct:paybill-validation-response-success")
            .id("paybill-validation-response-success")
            .unmarshal().json(WorkflowResponse.class)
            .setBody(exchange -> {
                WorkflowResponse workflowResponse = exchange.getIn()
                    .getBody(WorkflowResponse.class);
                if (workflowResponse == null || workflowResponse.transactionId() == null
                    || workflowResponse.transactionId().isBlank()
                    || workflowResponse.transactionId().trim().equalsIgnoreCase("null")) {
                    String transactionId = exchange.getProperty(TRANSACTION_ID, String.class);
                    String errorMessage = "Failed to start paybill workflow for transaction id "
                        + transactionId;
                    logger.error(errorMessage + ". Response from channel is {}", workflowResponse);
                    throw new RuntimeException(errorMessage);
                }
                ChannelValidationResponse validationResponse = exchange
                    .getProperty(CHANNEL_VALIDATION_RESPONSE, ChannelValidationResponse.class);
                workflowInstanceStore.put(validationResponse.transactionId(),
                    workflowResponse.transactionId());
                return new AirtelValidationResponse("Client validation successful",
                    validationResponse.transactionId(), validationResponse.clientName());
            })
            .marshal().json();

        from("direct:paybill-validation-response-failure")
            .id("paybill-validation-response-failure")
            .setBody(exchange -> {
                ChannelValidationResponse validationResponse = exchange.getIn()
                    .getBody(ChannelValidationResponse.class);
                String message = validationResponse.message() != null
                    ? validationResponse.message()
                    : "Client validation failed";
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 403);
                return new ErrorResponse(message, null);
            })
            .marshal().json();
    }
}
