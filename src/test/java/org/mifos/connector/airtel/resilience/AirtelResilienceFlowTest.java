package org.mifos.connector.airtel.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_REQUEST_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_TRANSACTION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.IS_TRANSACTION_PENDING;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_CODE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TIMER;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.CamelRouteTestSupport;
import org.mifos.connector.airtel.dto.CollectionRequestDto;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the collection and status routes against a simulated Airtel that is slow or unavailable.
 */
class AirtelResilienceFlowTest extends CamelRouteTestSupport {

    private static final String COLLECTION_PATH = "/merchant/v1/payments/";
    private static final String STATUS_PATH = "/standard/v1/payments/txn-1";
    private static final WireMockServer AIRTEL =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        AIRTEL.start();
    }

    @Autowired
    private AccessTokenStore accessTokenStore;

    @DynamicPropertySource
    static void airtelSettings(DynamicPropertyRegistry registry) {
        registry.add("airtel.credentials.rwanda.base-url", AIRTEL::baseUrl);
        registry.add("airtel.resilience.collection.read-timeout", () -> "300ms");
        registry.add("airtel.resilience.transaction-status.read-timeout", () -> "300ms");
        registry.add("airtel.resilience.transaction-status.retry.initial-backoff", () -> "10ms");
    }

    @AfterAll
    static void stopAirtel() {
        AIRTEL.stop();
    }

    @BeforeEach
    void resetAirtel() {
        AIRTEL.resetAll();
        accessTokenStore.setAccessToken("rwanda", "test-token", 3600);
    }

    @Test
    void collectionWithNoResponseIsAcceptedAndNotSentAgain() {
        AIRTEL.stubFor(post(COLLECTION_PATH)
            .willReturn(aResponse().withStatus(200).withFixedDelay(600)));

        Exchange exchange = collection(Long.MAX_VALUE);

        assertFalse(exchange.getProperty(TRANSACTION_FAILED, Boolean.class));
        AIRTEL.verify(1, postRequestedFor(urlEqualTo(COLLECTION_PATH)));
    }

    @Test
    void collectionWithNoTimeLeftBeforeTheJobDeadlineIsFailedWithoutBeingSent() {
        AIRTEL.stubFor(post(COLLECTION_PATH).willReturn(aResponse().withStatus(200)));

        Exchange exchange = collection(System.currentTimeMillis());

        assertTrue(exchange.getProperty(TRANSACTION_FAILED, Boolean.class));
        assertEquals("JOB_DEADLINE", exchange.getProperty(ERROR_CODE));
        AIRTEL.verify(0, postRequestedFor(urlEqualTo(COLLECTION_PATH)));
    }

    @Test
    void statusCheckWhileAirtelIsUnavailableIsRetriedThenLeftPending() {
        AIRTEL.stubFor(get(STATUS_PATH).willReturn(aResponse().withStatus(503)));

        Exchange exchange = camelContext.getEndpoint("direct:get-transaction-status-base")
            .createExchange();
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        exchange.setProperty(TRANSACTION_ID, "txn-1");
        exchange.setProperty(COLLECTION_TRANSACTION_ID, "txn-1");
        exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 1);
        exchange.setProperty(ZEEBE_ELEMENT_INSTANCE_KEY, 42L);
        exchange.setProperty(TIMER, "PT45S");
        producerTemplate.send("direct:get-transaction-status-base", exchange);

        assertTrue(exchange.getProperty(IS_TRANSACTION_PENDING, Boolean.class));
        assertNull(exchange.getProperty(TRANSACTION_FAILED));
        AIRTEL.verify(3, getRequestedFor(urlEqualTo(STATUS_PATH)));
    }

    private Exchange collection(long jobDeadline) {
        Exchange exchange = camelContext.getEndpoint("direct:collection-request-base")
            .createExchange();
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        exchange.setProperty(COLLECTION_REQUEST_BODY, collectionRequest());
        exchange.setProperty(AirtelHttpCaller.JOB_DEADLINE, jobDeadline);
        producerTemplate.send("direct:collection-request-base", exchange);
        return exchange;
    }

    private static CollectionRequestDto collectionRequest() {
        CollectionRequestDto.Subscriber subscriber = new CollectionRequestDto.Subscriber();
        subscriber.setCountry("RW");
        subscriber.setCurrency("RWF");
        subscriber.setMsisdn(250700000000L);
        CollectionRequestDto.Transaction transaction = new CollectionRequestDto.Transaction();
        transaction.setAmount(BigDecimal.valueOf(100));
        transaction.setCountry("RW");
        transaction.setCurrency("RWF");
        transaction.setId("txn-1");
        CollectionRequestDto dto = new CollectionRequestDto();
        dto.setSubscriber(subscriber);
        dto.setTransaction(transaction);
        dto.setReference("Payment to OAF");
        return dto;
    }
}
