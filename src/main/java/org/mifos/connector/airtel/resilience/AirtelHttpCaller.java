package org.mifos.connector.airtel.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultMessage;
import org.apache.http.conn.ConnectTimeoutException;
import org.mifos.connector.airtel.resilience.AirtelResilienceProperties.EndpointSettings;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Sends HTTP calls to Airtel with a per-endpoint timeout, bounded retries with jittered
 * exponential backoff, and a circuit breaker per country, endpoint and host. After the call,
 * {@link #CALL_OUTCOME} on the exchange says whether Airtel answered, the request never left, or
 * the result is unknown. When the exchange carries {@link #JOB_DEADLINE}, an attempt that could
 * not finish before it is not made.
 */
@Component
public class AirtelHttpCaller {

    public static final String CALL_OUTCOME = "airtelCallOutcome";
    public static final String CALL_FAILURE = "airtelCallFailure";
    /**
     * Epoch millis at which the Zeebe job that triggered the call times out.
     */
    public static final String JOB_DEADLINE = "airtelJobDeadline";

    private static final Pattern HOST = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/:?]+)");

    private static final Logger logger = LoggerFactory.getLogger(AirtelHttpCaller.class);

    private final ProducerTemplate producerTemplate;
    private final AirtelResilienceProperties properties;
    private final AirtelUtils airtelUtils;
    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.ofDefaults();
    private final RetryRegistry retries = RetryRegistry.ofDefaults();
    private final Map<AirtelEndpoint, CircuitBreakerConfig> circuitBreakerConfigs =
        new EnumMap<>(AirtelEndpoint.class);
    private final Map<AirtelEndpoint, RetryConfig> retryConfigs =
        new EnumMap<>(AirtelEndpoint.class);

    /**
     * Creates the caller and binds its circuit-breaker and retry metrics when a meter registry is
     * available.
     *
     * @param producerTemplate {@link ProducerTemplate}
     * @param properties       {@link AirtelResilienceProperties}
     * @param airtelUtils      {@link AirtelUtils}
     * @param meterRegistry    the meter registry, if any
     */
    public AirtelHttpCaller(ProducerTemplate producerTemplate,
                            AirtelResilienceProperties properties, AirtelUtils airtelUtils,
                            ObjectProvider<MeterRegistry> meterRegistry) {
        this.producerTemplate = producerTemplate;
        this.properties = properties;
        this.airtelUtils = airtelUtils;
        for (AirtelEndpoint endpoint : AirtelEndpoint.values()) {
            EndpointSettings settings = properties.settingsFor(endpoint);
            circuitBreakerConfigs.put(endpoint, circuitBreakerConfig(settings));
            retryConfigs.put(endpoint, retryConfig(settings));
        }
        circuitBreakers.getEventPublisher().onEntryAdded(added -> added.getAddedEntry()
            .getEventPublisher().onStateTransition(event -> {
                if (logger.isWarnEnabled()) {
                    logger.warn("event=airtel_circuit_state_changed circuit={} from={} to={}",
                        event.getCircuitBreakerName(), event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState());
                }
            }));
        retries.getEventPublisher().onEntryAdded(added -> added.getAddedEntry().getEventPublisher()
            .onRetry(event -> {
                if (logger.isWarnEnabled()) {
                    logger.warn("event=airtel_call_retry call={} attempt={} wait_ms={} reason={}",
                        event.getName(), event.getNumberOfRetryAttempts(),
                        event.getWaitInterval().toMillis(), reason(event.getLastThrowable()));
                }
            })
            .onError(event -> {
                if (logger.isErrorEnabled()) {
                    logger.error("event=airtel_retry_exhausted call={} attempts={} reason={}",
                        event.getName(), event.getNumberOfRetryAttempts(),
                        reason(event.getLastThrowable()));
                }
            }));
        meterRegistry.ifAvailable(registry -> {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(registry);
            TaggedRetryMetrics.ofRetryRegistry(retries).bindTo(registry);
        });
    }

    /**
     * A processor that calls the URI the expression yields for the exchange.
     *
     * @param endpoint the Airtel endpoint being called
     * @param uri      the Camel HTTP URI, without timeout parameters
     * @return the processor
     */
    public Processor call(AirtelEndpoint endpoint, Expression uri) {
        return exchange -> call(endpoint, uri.evaluate(exchange, String.class), exchange);
    }

    void call(AirtelEndpoint endpoint, String uri, Exchange exchange) {
        String name = "airtel-" + airtelUtils.getCountryFromExchange(exchange) + "-"
            + endpoint.id() + "-" + hostOf(uri);
        CircuitBreaker circuitBreaker =
            circuitBreakers.circuitBreaker(name, circuitBreakerConfigs.get(endpoint));
        Retry retry = retries.retry(name, retryConfigs.get(endpoint));
        EndpointSettings settings = properties.settingsFor(endpoint);
        String target = uri + (uri.contains("?") ? "&" : "?") + timeoutParameters(settings);
        Message request = exchange.getMessage().copy();
        long lastStart = exchange.getProperty(JOB_DEADLINE, Long.MAX_VALUE, Long.class)
            - properties.getJobDeadlineMargin().toMillis() - longestAttempt(settings);
        AtomicReference<FailedAttempt> lastFailure = new AtomicReference<>();

        try {
            Retry.decorateRunnable(retry, CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
                if (System.currentTimeMillis() > lastStart) {
                    throw outOfTime(name, lastFailure.get());
                }
                try {
                    attempt(target, exchange, request, settings);
                } catch (FailedAttempt failed) {
                    lastFailure.set(failed);
                    throw failed;
                }
            })).run();
            exchange.setProperty(CALL_OUTCOME, AirtelCallOutcome.RESPONDED);
        } catch (CallNotPermittedException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("event=airtel_call_rejected circuit={} state={}", name,
                    circuitBreaker.getState());
            }
            noResponse(exchange, AirtelCallOutcome.NOT_SENT, "circuit_open");
        } catch (FailedAttempt e) {
            if (e.outcome == AirtelCallOutcome.RESPONDED) {
                exchange.setProperty(CALL_OUTCOME, AirtelCallOutcome.RESPONDED);
                exchange.setProperty(CALL_FAILURE, e.getMessage());
            } else {
                noResponse(exchange, e.outcome, e.getMessage());
            }
        }
    }

    /**
     * Records a call that got no response. The message is emptied so the request (credentials,
     * payer details) is not mistaken for a response further down the route.
     *
     * @param exchange the exchange
     * @param outcome  {@link AirtelCallOutcome#NOT_SENT} or {@link AirtelCallOutcome#UNKNOWN}
     * @param reason   a short reason, safe to log
     */
    public static void noResponse(Exchange exchange, AirtelCallOutcome outcome, String reason) {
        exchange.setMessage(new DefaultMessage(exchange.getContext()));
        exchange.setProperty(CALL_OUTCOME, outcome);
        exchange.setProperty(CALL_FAILURE, reason);
    }

    /**
     * Whether Airtel answered the last call made with this exchange.
     *
     * @param exchange the exchange
     * @return false when the request was not sent or no response came back
     */
    public static boolean responded(Exchange exchange) {
        AirtelCallOutcome outcome = exchange.getProperty(CALL_OUTCOME, AirtelCallOutcome.class);
        return outcome == null || outcome == AirtelCallOutcome.RESPONDED;
    }

    private void attempt(String target, Exchange exchange, Message request,
                         EndpointSettings settings) {
        exchange.setException(null);
        exchange.setMessage(request.copy());
        producerTemplate.send(target, exchange);

        Exception exception = exchange.getException();
        if (exception != null) {
            exchange.setException(null);
            throw classify(exception, settings);
        }
        // The response stream can be read once; later steps log and parse it.
        exchange.getMessage().setBody(exchange.getMessage().getBody(String.class));
        int status = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, 0, Integer.class);
        if (settings.getRetry().getRetryStatusCodes().contains(status)) {
            throw new FailedAttempt(AirtelCallOutcome.RESPONDED, "status:" + status, true, true);
        }
        if (status >= 500) {
            throw new FailedAttempt(AirtelCallOutcome.RESPONDED, "status:" + status, false, true);
        }
    }

    /**
     * The failure to report when there is no time left for an attempt: the previous attempt's,
     * whose response (if any) is still on the message, or not sent when no attempt was made.
     * Neither retried nor counted against the circuit.
     */
    private static FailedAttempt outOfTime(String name, FailedAttempt previous) {
        if (logger.isWarnEnabled()) {
            logger.warn("event=airtel_call_skipped call={} reason=job_deadline after={}", name,
                reason(previous));
        }
        if (previous == null) {
            return new FailedAttempt(AirtelCallOutcome.NOT_SENT, "job_deadline", false, false);
        }
        return new FailedAttempt(previous.outcome, previous.getMessage(), false, false);
    }

    static String hostOf(String uri) {
        Matcher matcher = HOST.matcher(uri);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private static long longestAttempt(EndpointSettings settings) {
        return 2 * settings.getConnectTimeout().toMillis() + settings.getReadTimeout().toMillis();
    }

    static FailedAttempt classify(Exception exception, EndpointSettings settings) {
        String reason = exception.getClass().getSimpleName();
        if (neverSent(exception)) {
            return new FailedAttempt(AirtelCallOutcome.NOT_SENT, "not_sent:" + reason, true, true);
        }
        if (exception instanceof IOException) {
            return new FailedAttempt(AirtelCallOutcome.UNKNOWN, "no_response:" + reason,
                settings.getRetry().isRetryWhenOutcomeUnknown(), true);
        }
        // Not a network failure (bad URI, local error): the request did not leave and retrying
        // would not help.
        return new FailedAttempt(AirtelCallOutcome.NOT_SENT, "local_error:" + reason, false,
            false);
    }

    private static boolean neverSent(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
                || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                || cause instanceof SSLHandshakeException) {
                return true;
            }
        }
        return false;
    }

    private static String timeoutParameters(EndpointSettings settings) {
        long connect = settings.getConnectTimeout().toMillis();
        return "httpClient.connectTimeout=" + connect + "&httpClient.connectionRequestTimeout="
            + connect + "&httpClient.socketTimeout=" + settings.getReadTimeout().toMillis();
    }

    static CircuitBreakerConfig circuitBreakerConfig(EndpointSettings settings) {
        AirtelResilienceProperties.CircuitBreaker config = settings.getCircuitBreaker();
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(config.getSlidingWindowSize())
            .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
            .failureRateThreshold(config.getFailureRateThreshold())
            .waitDurationInOpenState(config.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(config.getPermittedCallsInHalfOpenState())
            .recordException(e -> e instanceof FailedAttempt failed && failed.countsAsFailure)
            .ignoreException(e -> e instanceof FailedAttempt failed && !failed.countsAsFailure)
            .build();
    }

    static RetryConfig retryConfig(EndpointSettings settings) {
        AirtelResilienceProperties.Retry config = settings.getRetry();
        return RetryConfig.custom().maxAttempts(config.getMaxAttempts())
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                config.getInitialBackoff(), config.getBackoffMultiplier(), config.getJitter()))
            .retryOnException(e -> e instanceof FailedAttempt failed && failed.retryable).build();
    }

    static String reason(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        return throwable instanceof FailedAttempt ? throwable.getMessage()
            : throwable.getClass().getSimpleName();
    }

    /**
     * One failed attempt. The message is a short reason safe to log: no URI, headers or body.
     */
    static final class FailedAttempt extends RuntimeException {

        final AirtelCallOutcome outcome;
        final boolean retryable;
        private final boolean countsAsFailure;

        FailedAttempt(AirtelCallOutcome outcome, String reason, boolean retryable,
                      boolean countsAsFailure) {
            super(reason, null, false, false);
            this.outcome = outcome;
            this.retryable = retryable;
            this.countsAsFailure = countsAsFailure;
        }
    }
}
