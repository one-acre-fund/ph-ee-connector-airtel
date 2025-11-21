package org.mifos.connector.airtel.routes;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.ACCESS_TOKEN;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_REQUEST_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_TRANSACTION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_RETRY_EXCEEDED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_INFORMATION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_FAILED;

import java.math.BigDecimal;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.AirtelMoneyConnectorApplicationTests;
import org.mifos.connector.airtel.dto.CollectionRequestDto;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
class AirtelMoneyRouteBuilderTest extends AirtelMoneyConnectorApplicationTests {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private AccessTokenStore accessTokenStore;

    private RouteDefinition findRoute(String id) {
        return camelContext.adapt(ModelCamelContext.class)
                .getRouteDefinitions().stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Route with id " + id + " not found"));
    }

    private CollectionRequestDto buildCollectionRequest() {
        CollectionRequestDto dto = new CollectionRequestDto();
        CollectionRequestDto.Subscriber subscriber = new CollectionRequestDto.Subscriber();
        subscriber.setCountry("rwanda");
        subscriber.setCurrency("RWF");
        subscriber.setMsisdn(250788123456L);
        CollectionRequestDto.Transaction transaction = new CollectionRequestDto.Transaction();
        transaction.setAmount(BigDecimal.valueOf(100));
        transaction.setCountry("rwanda");
        transaction.setCurrency("RWF");
        transaction.setId("txn-123");
        dto.setSubscriber(subscriber);
        dto.setTransaction(transaction);
        dto.setReference("Payment to OAF");
        return dto;
    }

    @DisplayName("Test collection-request-base route success and failure branches (lines 78-90)")
    @Test
    void testCollectionRequestBaseRouteBranches() throws Exception {

        // Use the static utility that accepts CamelContext/routeId and a builder lambda
        AdviceWithRouteBuilder.adviceWith(
                camelContext, // you can also pass camelContext.adapt(ModelCamelContext.class)
                "collection-request-base",
                a -> {
                    a.weaveByToUri("direct:get-access-token").replace().process(exchange -> {
                        // no-op, leaving ERROR_INFORMATION as is
                    });
                    a.weaveByToUri("direct:collection-request").replace().to("mock:collection-request");
                    a.weaveByToUri("direct:collection-response-handler").replace().to("mock:collection-response-handler");
                }
        );

        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        accessTokenStore.setAccessToken("rwanda", "valid-token", 3600);

        MockEndpoint mockCollectionReq = camelContext.getEndpoint("mock:collection-request", MockEndpoint.class);
        MockEndpoint mockCollectionRespHandler = camelContext.getEndpoint("mock:collection-response-handler", MockEndpoint.class);
        mockCollectionReq.expectedMessageCount(1);
        mockCollectionRespHandler.expectedMessageCount(1);

        Exchange successExchange = camelContext.getEndpoint("direct:collection-request-base").createExchange();
        successExchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        successExchange.setProperty(COLLECTION_REQUEST_BODY, buildCollectionRequest());
        producerTemplate.send("direct:collection-request-base", successExchange);

        mockCollectionReq.assertIsSatisfied();
        mockCollectionRespHandler.assertIsSatisfied();
        assertNotNull(successExchange.getProperty(ACCESS_TOKEN), "Access token should be set on success path");
        assertNull(successExchange.getProperty(TRANSACTION_FAILED), "Transaction failed flag should not be set in success path");

        mockCollectionReq.reset();
        mockCollectionRespHandler.reset();
        mockCollectionReq.expectedMessageCount(0);
        mockCollectionRespHandler.expectedMessageCount(0);

        Exchange failureExchange = camelContext.getEndpoint("direct:collection-request-base").createExchange();
        failureExchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        failureExchange.setProperty(COLLECTION_REQUEST_BODY, buildCollectionRequest());
        failureExchange.setProperty(ERROR_INFORMATION, "Simulated access token failure");
        producerTemplate.send("direct:collection-request-base", failureExchange);

        mockCollectionReq.assertIsSatisfied();
        mockCollectionRespHandler.assertIsSatisfied();
        assertNull(failureExchange.getProperty(ACCESS_TOKEN), "Access token must not be set when access token acquisition failed");
        assertTrue((Boolean) failureExchange.getProperty(TRANSACTION_FAILED), "Transaction failed flag should be true on failure path");
    }

    @DisplayName("Test get-transaction-status-base route success and failure branches (lines 153-169)")
    @Test
    void testGetTransactionStatusBaseRouteBranches() throws Exception {

        AdviceWithRouteBuilder.adviceWith(
                camelContext, // or camelContext.adapt(ModelCamelContext.class)
                "get-transaction-status-base",
                a -> {
                    a.weaveByToUri("direct:get-access-token").replace().process(exchange -> {
                        // no-op
                    });
                    a.weaveByToUri("direct:airtel-transaction-status").replace().to("mock:airtel-transaction-status");
                    a.weaveByToUri("direct:transaction-status-response-handler").replace().to("mock:transaction-status-response-handler");
                }
        );

        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        accessTokenStore.setAccessToken("rwanda", "valid-token-2", 3600);

        MockEndpoint mockStatus = camelContext.getEndpoint("mock:airtel-transaction-status", MockEndpoint.class);
        MockEndpoint mockStatusHandler = camelContext.getEndpoint("mock:transaction-status-response-handler", MockEndpoint.class);
        mockStatus.expectedMessageCount(1);
        mockStatusHandler.expectedMessageCount(1);

        Exchange successExchange = camelContext.getEndpoint("direct:get-transaction-status-base").createExchange();
        successExchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        successExchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 1);
        successExchange.setProperty(COLLECTION_TRANSACTION_ID, "txn-abc");
        producerTemplate.send("direct:get-transaction-status-base", successExchange);

        mockStatus.assertIsSatisfied();
        mockStatusHandler.assertIsSatisfied();
        assertNotNull(successExchange.getProperty(ACCESS_TOKEN), "Access token should be set on success status path");

        mockStatus.reset();
        mockStatusHandler.reset();
        mockStatus.expectedMessageCount(0);
        mockStatusHandler.expectedMessageCount(0);

        Exchange failureExchange = camelContext.getEndpoint("direct:get-transaction-status-base").createExchange();
        failureExchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        failureExchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 1);
        failureExchange.setProperty(ERROR_INFORMATION, "Simulated access token error");
        producerTemplate.send("direct:get-transaction-status-base", failureExchange);

        mockStatus.assertIsSatisfied();
        mockStatusHandler.assertIsSatisfied();
        assertNull(failureExchange.getProperty(ACCESS_TOKEN), "Access token must not be set when ERROR_INFORMATION is present");
        assertTrue((Boolean) failureExchange.getProperty(TRANSACTION_FAILED), "Transaction failed should be true on failure status path");
        assertTrue((Boolean) failureExchange.getProperty(IS_RETRY_EXCEEDED), "Retry exceeded should be true on failure status path");
    }

}
