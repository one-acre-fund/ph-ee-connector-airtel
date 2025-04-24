package org.mifos.connector.airtel.camel.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route builder for exposing spring boot actuator endpoints.
 */
@Component
public class ActuatorRouteBuilder extends RouteBuilder {

    @Value("${server.port:8080}")
    private String port;

    @Override
    public void configure() throws Exception {
        from("rest:GET:/actuator?matchOnUriPrefix=true")
            .setHeader(Exchange.HTTP_PATH, simple("${header.CamelHttpPath}"))
            .toD("http://localhost:" + port + "/actuator${header.CamelHttpPath}?bridgeEndpoint=true&throwExceptionOnFailure=false");
    }
}
