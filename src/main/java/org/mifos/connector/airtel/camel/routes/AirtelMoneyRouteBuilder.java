package org.mifos.connector.airtel.camel.routes;

import static org.mifos.connector.airtel.camel.config.CamelProperties.ACCESS_TOKEN;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_REQUEST_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_RESPONSE_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_RETRY_EXCEEDED;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_TRANSACTION_PENDING;
import static org.mifos.connector.airtel.camel.config.CamelProperties.LAST_RESPONSE_BODY;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AIRTEL_MONEY_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CALLBACK;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CALLBACK_RECEIVED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_CODE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_DESCRIPTION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_INFORMATION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.airtel.camel.processor.CollectionResponseProcessor;
import org.mifos.connector.airtel.dto.AirtelProps;
import org.mifos.connector.airtel.dto.CallbackDto;
import org.mifos.connector.airtel.dto.CollectionRequestDto;
import org.mifos.connector.airtel.dto.CollectionResponseDto;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.mifos.connector.airtel.util.ConnectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Route handlers for Airtel Money flow.
 */
@Component
public class AirtelMoneyRouteBuilder extends RouteBuilder {
    private static final Logger logger = LoggerFactory.getLogger(AirtelMoneyRouteBuilder.class);
    private final AccessTokenStore accessTokenStore;
    private final AirtelProps airtelProps;
    private final CollectionResponseProcessor collectionResponseProcessor;

    /**
     * Creates an instance of {@link AirtelMoneyRouteBuilder} with all required params.
     *
     * @param accessTokenStore            {@link AccessTokenStore}
     * @param airtelProps                 {@link AirtelProps}
     * @param collectionResponseProcessor {@link CollectionResponseProcessor}
     */
    public AirtelMoneyRouteBuilder(AccessTokenStore accessTokenStore, AirtelProps airtelProps,
                                   CollectionResponseProcessor collectionResponseProcessor) {
        this.accessTokenStore = accessTokenStore;
        this.airtelProps = airtelProps;
        this.collectionResponseProcessor = collectionResponseProcessor;
    }

    @Override
    public void configure() {

        /*
         * Starts the payment flow
         *
         * Step1: Authenticate the user by initiating [get-access-token] flow
         * Step2: On successful [Step1], directs to [collection-request] flow
         */
        from("direct:collection-request-base")
            .id("collection-request-base")
            .log(LoggingLevel.INFO, "Starting collection request flow")
            .log(LoggingLevel.INFO, "Starting buy goods flow with retry count: "
                + airtelProps.getMaxRetryCount())
            .to("direct:get-access-token")
            .process(exchange -> exchange.setProperty(ACCESS_TOKEN,
                accessTokenStore.getAccessToken()))
            .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
            .to("direct:collection-request")
            .log(LoggingLevel.INFO, "Status: ${header.CamelHttpResponseCode}")
            .log(LoggingLevel.INFO, "Collection request API response: ${body}")
            .to("direct:collection-response-handler");

        /*
         * Takes the access toke and payment request and forwards the requests
         * to Airtel collection API.
         */
        from("direct:collection-request")
            .removeHeader("*")
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .setHeader("Authorization", simple("Bearer ${exchangeProperty." + ACCESS_TOKEN + "}"))
            .setHeader("X-Country", simple("${exchangeProperty.country}"))
            .setHeader("X-Currency", simple("${exchangeProperty.currency}"))
            .setBody(exchange -> {
                CollectionRequestDto collectionRequestDto =
                    (CollectionRequestDto) exchange.getProperty(COLLECTION_REQUEST_BODY);
                logger.info("COLLECTION REQUEST BODY: \n\n..\n\n..\n\n.. " + collectionRequestDto);
                return collectionRequestDto;
            })
            .marshal().json(JsonLibrary.Jackson)
            .toD(airtelProps.getApi().getBaseUrl() + airtelProps.getApi().getCollectionEndpoint()
                + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"
                + ConnectionUtils.getConnectionTimeoutDsl(airtelProps.getTimeout()))
            .process(exchange -> exchange.setProperty(COLLECTION_RESPONSE_BODY,
                exchange.getIn().getBody(String.class)))
            .log(LoggingLevel.INFO, "Airtel collection API called, response: \n\n ${body}");

        /*
         * Route to handle Airtel collection API responses
         */
        from("direct:collection-response-handler")
            .id("collection-response-handler")
            .choice()
            .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("200"))
            .log(LoggingLevel.INFO, "Collection request successful")
            .setProperty(TRANSACTION_FAILED, constant(false))
            .otherwise()
            .log(LoggingLevel.ERROR, "Collection request unsuccessful")
            .unmarshal().json(CollectionResponseDto.class)
            .process(exchange -> {
                exchange.setProperty(TRANSACTION_FAILED, true);
                CollectionResponseDto.Status status = exchange.getIn()
                    .getBody(CollectionResponseDto.class).getStatus();
                exchange.setProperty(ERROR_CODE, status.getResponseCode());
                exchange.setProperty(ERROR_DESCRIPTION, status.getMessage());
                exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
            });

        /*
         * Starts the payment flow
         *
         * Step1: Authenticate the user by initiating [get-access-token] flow
         * Step2: On successful [Step1], directs to [airtel-transaction-status] flow
         */
        from("direct:get-transaction-status-base")
            .id("get-transaction-status-base")
            .log(LoggingLevel.INFO, "Starting Airtel transaction status flow")
            .choice()
            .when(exchangeProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT)
                .isLessThanOrEqualTo(airtelProps.getMaxRetryCount()))
            .to("direct:get-access-token")
            .process(exchange -> exchange.setProperty(ACCESS_TOKEN,
                accessTokenStore.getAccessToken()))
            .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
            .to("direct:airtel-transaction-status")
            .log(LoggingLevel.INFO, "Status: ${header.CamelHttpResponseCode}")
            .log(LoggingLevel.INFO, "Transaction status API response: ${body}")
            .to("direct:transaction-status-response-handler")
            .otherwise()
            .process(exchange -> {
                exchange.setProperty(IS_RETRY_EXCEEDED, true);
                exchange.setProperty(TRANSACTION_FAILED, true);
            })
            .process(collectionResponseProcessor);

        /*
         * Retrieves the transaction status by calling the Airtel status endpoint
         */
        from("direct:airtel-transaction-status")
            .removeHeader("*")
            .setHeader(Exchange.HTTP_METHOD, constant("GET"))
            .setHeader("Content-Type", constant("application/json"))
            .setHeader("X-Country", simple("${exchangeProperty.country}"))
            .setHeader("X-Currency", simple("${exchangeProperty.currency}"))
            .setHeader("Authorization", simple("Bearer ${exchangeProperty." + ACCESS_TOKEN + "}"))
            .toD(airtelProps.getApi().getBaseUrl() + airtelProps.getApi().getStatusEndpoint()
                + "/${exchangeProperty." + TRANSACTION_ID + "}"
                + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"
                + ConnectionUtils.getConnectionTimeoutDsl(airtelProps.getTimeout()))
            .log(LoggingLevel.INFO, "Airtel Transaction status API called, response: \n\n ${body}");

        /*
         * Route to process the transaction status response received from Airtel
         */
        from("direct:transaction-status-response-handler")
            .id("transaction-status-response-handler")
            .unmarshal().json(CollectionResponseDto.class)
            .choice()
            .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("200"))
            .log(LoggingLevel.INFO, "Transaction status request successful")
            .process(exchange -> {
                CollectionResponseDto response = exchange.getIn()
                    .getBody(CollectionResponseDto.class);
                CollectionResponseDto.Data.Transaction transactionData = response.getData()
                    .getTransaction();
                if ("TS".equals(transactionData.getStatus())) {
                    exchange.setProperty(TRANSACTION_FAILED, false);
                    exchange.setProperty(AIRTEL_MONEY_ID, transactionData.getAirtelMoneyId());
                } else if ("TF".equals(transactionData.getStatus())) {
                    exchange.setProperty(TRANSACTION_FAILED, true);
                    CollectionResponseDto.Status status = response.getStatus();
                    exchange.setProperty(ERROR_CODE, status.getResponseCode());
                    exchange.setProperty(ERROR_DESCRIPTION, status.getMessage());
                    exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
                } else {
                    exchange.setProperty(IS_TRANSACTION_PENDING, true);
                }
                exchange.setProperty(LAST_RESPONSE_BODY, exchange.getIn().getBody(String.class));
            })
            .process(collectionResponseProcessor)
            .otherwise()
            .log(LoggingLevel.ERROR, "Transaction status request unsuccessful")
            .process(exchange -> logger.error("Body:" + exchange.getIn().getBody(String.class)))
            .setProperty(TRANSACTION_FAILED, constant(true))
            .process(collectionResponseProcessor);

        /*
           Endpoint for receiving the callback form Airtel
         */
        from("rest:POST:/collections/callback")
            .id("collections-callback")
            .log(LoggingLevel.INFO, "Callback body \n\n..\n\n..\n\n.. ${body}")
            .unmarshal().json(CallbackDto.class)
            .to("direct:callback-handler");

        /*
         * Handles Airtel callback request
         */
        from("direct:callback-handler")
            .id("callback-handler")
            .log(LoggingLevel.INFO, "Handling callback body")
            .process(exchange -> {
                CallbackDto callbackDto = exchange.getIn().getBody(CallbackDto.class);
                CallbackDto.Transaction transaction = callbackDto.getTransaction();
                exchange.setProperty(TRANSACTION_ID, transaction.getId());
                exchange.setProperty(CALLBACK_RECEIVED, true);
                exchange.setProperty(CALLBACK, callbackDto.toString());
                exchange.setProperty(AIRTEL_MONEY_ID, transaction.getAirtelMoneyId());

                logger.info("\n\n MTN Callback " + callbackDto + "\n");
                logger.info("\n\n Correlation Key " + transaction.getId());

                if ("TS".equals(transaction.getStatusCode())) {
                    exchange.setProperty(TRANSACTION_FAILED, false);
                } else if ("TF".equals(transaction.getStatusCode())) {
                    exchange.setProperty(TRANSACTION_FAILED, true);
                    exchange.setProperty(ERROR_DESCRIPTION, transaction.getMessage());
                    exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
                }
            })
            .choice()
            .when(exchangeProperty(TRANSACTION_FAILED).isNotNull())
            .process(collectionResponseProcessor)
            .otherwise()
            .log("Transaction is in intermediate state. "
                + "Hence, an attempt will be made to get transaction status later");
    }
}
