package org.mifos.connector.airtel.auth;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.CamelRouteTestSupport;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_INFORMATION;

class AuthRoutesTest extends CamelRouteTestSupport {

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

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        exchange.getIn().setBody(inputJson);
        producerTemplate.send("direct:access-token-save", exchange);

        LocalDateTime actualExpirationTime = accessTokenStore.getExpiresOn("rwanda");
        LocalDateTime expectedExpirationTime = LocalDateTime.now().plusSeconds(3600);

        // Assertions
        Assertions.assertEquals("test-access-token", accessTokenStore.getAccessToken("rwanda").getToken());
        assertNull(exchange.getProperty(ERROR_INFORMATION));
        assertTrue(
                !actualExpirationTime.isBefore(expectedExpirationTime.minusSeconds(5))
                        && !actualExpirationTime.isAfter(expectedExpirationTime.plusSeconds(5)),
                "Expiration time is within tolerance range");
    }

    @DisplayName("Non-positive expires_in sets ERROR_INFORMATION and does not leave a usable token")
    @Test
    void testAccessTokenSaveRoute_nonPositiveExpirySetsError() {
        String inputJson = """
                {
                  "access_token": "bad-token",
                  "expires_in": 0
                }
                """;

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        exchange.getIn().setBody(inputJson);
        producerTemplate.send("direct:access-token-save", exchange);

        assertNotNull(exchange.getProperty(ERROR_INFORMATION));
        assertTrue(exchange.getProperty(ERROR_INFORMATION, String.class)
                .contains("non-positive expiry"));
        assertNull(accessTokenStore.getAccessToken("rwanda"));
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
        camelContext.getRouteController().stopRoute("access-token-fetch");
        AdviceWithRouteBuilder.adviceWith(camelContext, "access-token-fetch", a ->
            a.interceptSendToEndpoint("https://*")
                .skipSendToOriginalEndpoint()
                .to("mock:auth-https-sink")
        );
        camelContext.getRouteController().startRoute("access-token-fetch");

        MockEndpoint mockSink = camelContext.getEndpoint("mock:auth-https-sink", MockEndpoint.class);
        mockSink.expectedMessageCount(1);

        Exchange exchange = camelContext.getEndpoint("direct:access-token-fetch").createExchange();
        exchange.setProperty(PLATFORM_TENANT_ID, "rwanda");
        producerTemplate.send("direct:access-token-fetch", exchange);
        assertEquals("https://openapiuat.airtel.co.rw", exchange.getProperty("baseUrl"),
                "baseUrl should resolve to the rwanda base URL when PLATFORM_TENANT_ID=rwanda");
    }

}
