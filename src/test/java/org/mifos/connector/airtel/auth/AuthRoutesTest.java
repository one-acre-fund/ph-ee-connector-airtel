package org.mifos.connector.airtel.auth;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.AirtelMoneyConnectorApplicationTests;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;

class AuthRoutesTest extends AirtelMoneyConnectorApplicationTests {

    @Autowired
    private FluentProducerTemplate fluentProducerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @DisplayName("Test Access Token Save Route")
    @Test
    void testAccessTokenSaveRoute() {

        // JSON input for the route
        String inputJson = """
                {
                  "access_token": "test-access-token",
                  "expires_in": 3600
                }
                """;

        // Send input to the route
        fluentProducerTemplate.to("direct:access-token-save").withBody(inputJson).send();

        LocalDateTime actualExpirationTime = accessTokenStore.getExpiresOn("rwanda");
        LocalDateTime expectedExpirationTime = LocalDateTime.now().plusSeconds(3600);

        // Assertions
        Assertions.assertEquals("test-access-token", accessTokenStore.getAccessToken("rwanda").getToken());
        assertTrue(
                !actualExpirationTime.isBefore(expectedExpirationTime.minusSeconds(5))
                        && !actualExpirationTime.isAfter(expectedExpirationTime.plusSeconds(5)),
                "Expiration time is within tolerance range");
    }

    @DisplayName("Test Access Token Error Route")
    @Test
    void testAccessTokenErrorRoute() {
        String errorBody = "Test error";
        Assertions.assertDoesNotThrow(() -> fluentProducerTemplate.to("direct:access-token-error").withBody(errorBody)
                .withHeader("Test-Header", "HeaderValue").send());
    }

    @DisplayName("Test Access Token Fetch Route")
    @Test
    void testAccessTokenFetchRoute() {
        Assertions
                .assertDoesNotThrow(() -> fluentProducerTemplate.to("direct:access-token-fetch").withBody(null).send());
    }

    @DisplayName("Test Get Access Token Route - Valid Token")
    @Test
    void testGetAccessTokenRouteValidToken() {
        accessTokenStore.setAccessToken("rwanda", "valid-token", 3600);
        Assertions.assertDoesNotThrow(() -> fluentProducerTemplate.to("direct:get-access-token").withBody(null).send());
    }

    @DisplayName("Test Get Access Token Route - Expired Token")
    @Test
    void testGetAccessTokenRouteExpiredToken() {
        accessTokenStore.setAccessToken("rwanda", "expired-token", -3600);
        Assertions.assertDoesNotThrow(() -> fluentProducerTemplate.to("direct:get-access-token").withBody(null).send());
    }

    @DisplayName("Test access-token-fetch route resolves baseUrl from PLATFORM_TENANT_ID via getCountryFromExchange")
    @Test
    void testAccessTokenFetchResolvesCountryBaseUrl() throws Exception {
        AdviceWithRouteBuilder.adviceWith(camelContext, "access-token-fetch", a ->
            a.interceptSendToEndpoint("https://*")
                .skipSendToOriginalEndpoint()
                .to("mock:auth-https-sink")
        );

        MockEndpoint mockSink = camelContext.getEndpoint("mock:auth-https-sink", MockEndpoint.class);
        mockSink.expectedMessageCount(1);

        Exchange exchange = camelContext.getEndpoint("direct:access-token-fetch").createExchange();
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        producerTemplate.send("direct:access-token-fetch", exchange);
        assertEquals("https://openapiuat.airtel.africa.co.rw", exchange.getProperty("baseUrl"),
                "baseUrl should resolve to the rwanda base URL when PLATFORM_TENANT_ID=rwanda");
    }

}
