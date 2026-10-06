package org.mifos.connector.airtel.routes;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.CamelRouteTestSupport;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExceptionHandlerRoutesTest extends CamelRouteTestSupport {

    @Test
    @DisplayName("BeanValidationException returns 400 with field errors")
    void beanValidation_returns400() throws Exception {
        camelContext.getRouteController().stopRoute("airtel-validation");
        AdviceWithRouteBuilder.adviceWith(camelContext, "airtel-validation", a ->
                a.replaceFromWith("direct:airtel-validation-bean-fail"));
        camelContext.getRouteController().startRoute("airtel-validation");

        Exchange exchange = camelContext.getEndpoint("direct:airtel-validation-bean-fail")
                .createExchange();
        // Missing required fields → BeanValidationException via bean-validator
        exchange.getIn().setBody("""
                {
                  "transactionId": "",
                  "accountNumber": "",
                  "currency": "ZZ",
                  "msisdn": ""
                }
                """);
        producerTemplate.send("direct:airtel-validation-bean-fail", exchange);

        assertEquals(400, exchange.getIn().getHeader(HTTP_RESPONSE_CODE));
        String body = exchange.getIn().getBody(String.class);
        assertTrue(body.contains("Errors exist in the request body"));
    }
}
