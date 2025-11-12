package org.mifos.connector.airtel.camel.routes;

import static org.mifos.connector.airtel.util.AirtelUtils.getCountryFromExchange;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_INFORMATION;

import java.time.LocalDateTime;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.airtel.dto.AirtelProps;
import org.mifos.connector.airtel.dto.AuthResponseDto;
import org.mifos.connector.airtel.store.AccessTokenStore;
import org.mifos.connector.airtel.util.ConnectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Route handlers for authentication.
 */
@Component
public class AuthRouteBuilder extends RouteBuilder {
    private static final Logger logger = LoggerFactory.getLogger(AuthRouteBuilder.class);
    private final AccessTokenStore accessTokenStore;
    private final AirtelProps airtelProps;

    public AuthRouteBuilder(AccessTokenStore accessTokenStore, AirtelProps airtelProps) {
        this.accessTokenStore = accessTokenStore;
        this.airtelProps = airtelProps;
    }

    @Override
    public void configure() {

        /*
          Access Token check validity and return value
         */
        from("direct:get-access-token")
            .id("get-access-token")
            .choice()
            .when(exchange -> accessTokenStore.isValid(getCountryFromExchange(exchange), LocalDateTime.now()))
            .log("Access token valid. Continuing.")
            .otherwise()
            .log("Access token expired or not present")
            .to("direct:access-token-fetch")
            .choice()
            .when(header("CamelHttpResponseCode").isEqualTo("200"))
            .log("Access Token Fetch Successful")
            .to("direct:access-token-save")
            .otherwise()
            .log("Access Token Fetch Unsuccessful")
            .to("direct:access-token-error");


        /*
          Fetches Access Token from Airtel API
         */
        from("direct:access-token-fetch")
            .id("access-token-fetch")
            .log(LoggingLevel.INFO, "Fetching access token")
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .setBody(exchange -> airtelProps.getCredentials(getCountryFromExchange(exchange)))
            .marshal().json(JsonLibrary.Jackson)
            .toD(airtelProps.getApi().getBaseUrl() + airtelProps.getApi().getAuthEndpoint()
                + "?bridgeEndpoint=true&throwExceptionOnFailure=false&"
                + ConnectionUtils.getConnectionTimeoutDsl(airtelProps.getTimeout()));

        /*
          Saves Access Token to AccessTokenStore
         */
        from("direct:access-token-save")
            .id("access-token-save")
            .unmarshal().json(AuthResponseDto.class)
            .process(exchange -> {
                AuthResponseDto response = exchange.getIn().getBody(AuthResponseDto.class);
                accessTokenStore.setAccessToken(getCountryFromExchange(exchange),
                        response.accessToken(), response.expiresIn());
                logger.info("Saved Access Token");
            });

        /*
          Error handling route
         */
        from("direct:access-token-error")
            .id("access-token-error")
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                logger.error(body);
                exchange.setProperty(ERROR_INFORMATION, body);
            });

    }
}
