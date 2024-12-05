package org.mifos.connector.airtel.camel.routes;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.ACCOUNT_HOLDING_INSTITUTION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.AMS_NAME;
import static org.mifos.connector.airtel.camel.config.CamelProperties.AMS_URL;
import static org.mifos.connector.airtel.camel.config.CamelProperties.BRIDGE_ENDPOINT_QUERY_PARAM;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CHANNEL_VALIDATION_RESPONSE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CORRELATION_ID_HEADER;
import static org.mifos.connector.airtel.camel.config.CamelProperties.DEFAULT;
import static org.mifos.connector.airtel.camel.config.CamelProperties.JSON_CONTENT_TYPE;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PRIMARY_IDENTIFIER;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PRIMARY_IDENTIFIER_VALUE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INITIATOR_FSP_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

import java.util.Map;
import org.apache.camel.builder.RouteBuilder;
import org.mifos.connector.airtel.dto.AirtelValidationRequest;
import org.mifos.connector.airtel.dto.AirtelValidationResponse;
import org.mifos.connector.airtel.dto.ChannelValidationRequest;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.airtel.dto.ErrorResponse;
import org.mifos.connector.airtel.dto.PaybillProps;
import org.mifos.connector.airtel.dto.WorkflowResponse;
import org.mifos.connector.airtel.exception.WorkflowException;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Routes for the paybill workflow.
 */
@Component
public class PaybillRouteBuilder extends RouteBuilder {

    private final PaybillProps paybillProps;
    private final String channelUrl;

    public PaybillRouteBuilder(PaybillProps paybillProps,
                               @Value("${channel.host}") String channelUrl) {
        this.paybillProps = paybillProps;
        this.channelUrl = channelUrl;
    }

    @Override
    public void configure() {

        from("rest:POST:/validation")
            .id("airtel-validation")
            .unmarshal().json(AirtelValidationRequest.class)
            .log("Received airtel validation request with transaction id: ${body.transactionId}, "
                + "body: ${body} ")
            .to("bean-validator:airtel-validation-request-validator")
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


        from("direct:account-status")
            .id("account-status")
            .setBody(exchange -> {
                AirtelValidationRequest request = exchange.getIn()
                    .getBody(AirtelValidationRequest.class);
                Map<String, PaybillProps.AmsProps> shortCodes = paybillProps.getAmsShortCodes();
                PaybillProps.AmsProps amsProps = shortCodes
                    .getOrDefault(request.businessShortCode(), shortCodes.get(DEFAULT));
                exchange.getIn().removeHeaders("*");
                exchange.getIn().setHeader(CONTENT_TYPE, JSON_CONTENT_TYPE);
                exchange.getIn().setHeader(AMS_URL, amsProps.getAmsUrl());
                exchange.getIn().setHeader(AMS_NAME, amsProps.getAmsName());
                exchange.getIn().setHeader(ACCOUNT_HOLDING_INSTITUTION_ID,
                    paybillProps.getAccountHoldingInstitutionId());
                exchange.setProperty(INITIATOR_FSP_ID, request.businessShortCode());
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
                return AirtelUtils.createGsmaTransferRequest(validationResponse,
                    businessShortCode, primaryIdentifier, primaryIdentifierValue);
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
                    throw new WorkflowException("Failed to start paybill workflow");
                }
                ChannelValidationResponse validationResponse = exchange
                    .getProperty(CHANNEL_VALIDATION_RESPONSE, ChannelValidationResponse.class);
                return new AirtelValidationResponse("Client validation successful",
                    validationResponse.transactionId(), validationResponse.clientName());
            })
            .marshal().json();

        from("direct:paybill-validation-response-failure")
            .id("paybill-validation-response-failure")
            .setBody(exchange -> {
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 403);
                return new ErrorResponse("Client validation failed", null);
            })
            .marshal().json();
    }
}
