package org.mifos.connector.airtel.camel.routes;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;

import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.component.bean.validator.BeanValidationException;
import org.mifos.connector.airtel.dto.ErrorResponse;
import org.mifos.connector.airtel.exception.WorkflowException;
import org.mifos.connector.airtel.exception.WorkflowNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Exception handler for the camel routes.
 */
@Component
public class ExceptionHandler extends RouteConfigurationBuilder {
    @Override
    public void configuration() {
        routeConfiguration()
            .onException(BeanValidationException.class)
            .handled(true)
            .process(exchange -> {
                BeanValidationException exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                    BeanValidationException.class);
                Map<String, String> errors = new HashMap<>();
                exception.getConstraintViolations().forEach(violation -> {
                    errors.put(violation.getPropertyPath().toString(), violation.getMessage());
                });
                ErrorResponse response = new ErrorResponse(
                    "Errors exist in the request body", errors);
                exchange.getIn().setBody(response);
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 400);
            })
            .marshal().json();

        routeConfiguration()
            .onException(WorkflowNotFoundException.class)
            .handled(true)
            .process(exchange -> {
                WorkflowNotFoundException exception = exchange
                    .getProperty(Exchange.EXCEPTION_CAUGHT, WorkflowNotFoundException.class);
                exchange.getIn().setBody(new ErrorResponse(exception.getMessage(), null));
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 404);
            })
            .marshal().json();

        routeConfiguration()
            .onException(WorkflowException.class)
            .handled(true)
            .log(LoggingLevel.ERROR, "Workflow error occurred: ${exception.stacktrace}")
            .process(handleInternalServerError())
            .marshal().json();

        routeConfiguration()
            .onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.ERROR, "Caught exception: ${exception.stacktrace}")
            .process(handleInternalServerError())
            .marshal().json();
    }

    private static Processor handleInternalServerError() {
        return exchange -> {
            ErrorResponse response = new ErrorResponse(
                "An error occurred while processing the request", null);
            exchange.getIn().setBody(response);
            exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 500);
        };
    }
}
