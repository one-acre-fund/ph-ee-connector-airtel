package org.mifos.connector.airtel.camel.routes;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;

import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.component.bean.validator.BeanValidationException;
import org.mifos.connector.airtel.dto.ErrorResponse;
import org.mifos.connector.airtel.exception.TransactionAlreadyExistsException;
import org.mifos.connector.airtel.exception.WorkflowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Exception handler for the camel routes.
 */
@Component
public class ExceptionHandler extends RouteConfigurationBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    @Override
    public void configuration() {
        routeConfiguration()
            .onException(BeanValidationException.class)
            .handled(true)
            .process(exchange -> {
                BeanValidationException exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                    BeanValidationException.class);
                logger.error("Request body validation failed", exception);
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
                logger.error("Workflow instance not found", exception);
                exchange.getIn().setBody(new ErrorResponse(exception.getMessage(), null));
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 404);
            })
            .marshal().json();

        routeConfiguration()
            .onException(TransactionAlreadyExistsException.class)
            .handled(true)
            .process(exchange -> {
                TransactionAlreadyExistsException exception = exchange
                    .getProperty(Exchange.EXCEPTION_CAUGHT,
                        TransactionAlreadyExistsException.class);
                logger.error("Transaction already exists", exception);
                exchange.getIn().setBody(new ErrorResponse(exception.getMessage(), null));
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 409);
            })
            .marshal().json();

        routeConfiguration()
            .onException(Exception.class)
            .handled(true)
            .process(exchange -> {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                    Exception.class);
                logger.error("Caught exception while processing request", exception);
                ErrorResponse response = new ErrorResponse(
                    "An error occurred while processing the request", null);
                exchange.getIn().setBody(response);
                exchange.getIn().setHeader(HTTP_RESPONSE_CODE, 500);
            })
            .marshal().json();
    }
}
