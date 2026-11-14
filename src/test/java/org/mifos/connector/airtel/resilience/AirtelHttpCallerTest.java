package org.mifos.connector.airtel.resilience;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.mifos.connector.airtel.util.CountryProps;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class AirtelHttpCallerTest {

    private static final String PATH = "/merchant/v1/payments/";
    private static final String COLLECTION = "airtel-rwanda-collection-localhost";

    private WireMockServer airtel;
    private CamelContext camelContext;
    private ProducerTemplate producerTemplate;
    private AirtelResilienceProperties properties;
    private MeterRegistry meterRegistry;
    private AirtelUtils airtelUtils;
    private AirtelHttpCaller caller;

    @BeforeEach
    void setUp() {
        airtel = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        airtel.start();
        camelContext = new DefaultCamelContext();
        camelContext.start();
        producerTemplate = camelContext.createProducerTemplate();
        meterRegistry = new SimpleMeterRegistry();
        CountryProps countryProps = new CountryProps();
        countryProps.setDefaultTenant("malawi");
        airtelUtils = new AirtelUtils(countryProps);
        properties = new AirtelResilienceProperties();
        for (AirtelEndpoint endpoint : AirtelEndpoint.values()) {
            AirtelResilienceProperties.EndpointSettings settings = properties.settingsFor(endpoint);
            settings.setConnectTimeout(Duration.ofMillis(500));
            settings.setReadTimeout(Duration.ofMillis(300));
            settings.getRetry().setInitialBackoff(Duration.ofMillis(10));
            settings.getCircuitBreaker().setSlidingWindowSize(10);
            settings.getCircuitBreaker().setMinimumNumberOfCalls(10);
            settings.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofMillis(300));
            settings.getCircuitBreaker().setPermittedCallsInHalfOpenState(1);
        }
        caller = newCaller();
    }

    @AfterEach
    void tearDown() throws Exception {
        producerTemplate.close();
        camelContext.close();
        airtel.stop();
    }

    @Test
    void successfulCallIsSentOnce() {
        stub(200);

        Exchange exchange = call(AirtelEndpoint.COLLECTION, "rwanda");

        assertEquals(AirtelCallOutcome.RESPONDED,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertTrue(AirtelHttpCaller.responded(exchange));
        assertEquals(200, status(exchange));
        assertEquals("{\"status\":\"ok\"}", exchange.getMessage().getBody(String.class));
        airtel.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void retryableStatusIsRetriedUntilItSucceeds() {
        airtel.stubFor(post(PATH).inScenario("flaky").whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        airtel.stubFor(post(PATH).inScenario("flaky").whenScenarioStateIs("recovered")
            .willReturn(aResponse().withStatus(200)));

        Exchange exchange = call(AirtelEndpoint.TRANSACTION_STATUS, "rwanda");

        assertEquals(200, status(exchange));
        airtel.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void retriesStopAtMaxAttemptsAndKeepTheLastResponse() {
        stub(503);

        Exchange exchange = call(AirtelEndpoint.TRANSACTION_STATUS, "rwanda");

        assertEquals(AirtelCallOutcome.RESPONDED,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertEquals(503, status(exchange));
        assertEquals("status:503", exchange.getProperty(AirtelHttpCaller.CALL_FAILURE));
        airtel.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void collectionServerErrorIsNotRetried() {
        stub(503);

        Exchange exchange = call(AirtelEndpoint.COLLECTION, "rwanda");

        assertEquals(503, status(exchange));
        airtel.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void clientErrorIsNotRetried() {
        stub(400);

        Exchange exchange = call(AirtelEndpoint.TOKEN, "rwanda");

        assertEquals(400, status(exchange));
        assertNull(exchange.getProperty(AirtelHttpCaller.CALL_FAILURE));
        airtel.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void connectionFailureIsRetriedAndReportedAsNotSent() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        Exchange exchange = newExchange("rwanda");
        caller.call(AirtelEndpoint.COLLECTION,
            "http://localhost:" + closedPort + PATH + "?throwExceptionOnFailure=false", exchange);

        assertEquals(AirtelCallOutcome.NOT_SENT,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertFalse(AirtelHttpCaller.responded(exchange));
        assertTrue(exchange.getProperty(AirtelHttpCaller.CALL_FAILURE, String.class)
            .startsWith("not_sent:"));
        assertNull(exchange.getException());
        assertNull(exchange.getMessage().getHeader("Authorization"),
            "the request must not be left on the message");
        assertNull(exchange.getMessage().getBody());
        assertEquals(1.0, meterRegistry.get("resilience4j.retry.calls").tag("name", COLLECTION)
            .tag("kind", "failed_with_retry").functionCounter().count());
    }

    @Test
    void collectionTimeoutIsNotRetriedAndReportedAsUnknown() {
        airtel.stubFor(post(PATH).willReturn(aResponse().withStatus(200).withFixedDelay(600)));

        Exchange exchange = call(AirtelEndpoint.COLLECTION, "rwanda");

        assertEquals(AirtelCallOutcome.UNKNOWN,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertTrue(exchange.getProperty(AirtelHttpCaller.CALL_FAILURE, String.class)
            .startsWith("no_response:"));
        airtel.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void statusTimeoutIsRetried() {
        airtel.stubFor(post(PATH).willReturn(aResponse().withStatus(200).withFixedDelay(600)));

        Exchange exchange = call(AirtelEndpoint.TRANSACTION_STATUS, "rwanda");

        assertEquals(AirtelCallOutcome.UNKNOWN,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        airtel.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void circuitOpensFailsFastThenClosesAfterSuccessfulProbe() {
        singleAttemptWithSmallWindow();
        stub(503);
        call(AirtelEndpoint.COLLECTION, "rwanda");
        call(AirtelEndpoint.COLLECTION, "rwanda");

        Exchange rejected = call(AirtelEndpoint.COLLECTION, "rwanda");

        assertEquals(AirtelCallOutcome.NOT_SENT,
            rejected.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertEquals("circuit_open", rejected.getProperty(AirtelHttpCaller.CALL_FAILURE));
        airtel.verify(2, postRequestedFor(urlEqualTo(PATH)));
        assertEquals(1.0, circuitState(COLLECTION, "open"));

        waitForOpenStateToEnd();
        airtel.resetAll();
        stub(200);
        Exchange probe = call(AirtelEndpoint.COLLECTION, "rwanda");

        assertEquals(200, status(probe));
        assertEquals(1.0, circuitState(COLLECTION, "closed"));
    }

    @Test
    void failedProbeReopensTheCircuit() {
        singleAttemptWithSmallWindow();
        stub(503);
        call(AirtelEndpoint.COLLECTION, "rwanda");
        call(AirtelEndpoint.COLLECTION, "rwanda");
        waitForOpenStateToEnd();

        call(AirtelEndpoint.COLLECTION, "rwanda");

        assertEquals(1.0, circuitState(COLLECTION, "open"));
        assertEquals("circuit_open", call(AirtelEndpoint.COLLECTION, "rwanda")
            .getProperty(AirtelHttpCaller.CALL_FAILURE));
        airtel.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void eachCountryHasItsOwnCircuit() {
        singleAttemptWithSmallWindow();
        stub(503);
        call(AirtelEndpoint.COLLECTION, "rwanda");
        call(AirtelEndpoint.COLLECTION, "rwanda");
        airtel.resetAll();
        stub(200);

        assertEquals("circuit_open", call(AirtelEndpoint.COLLECTION, "rwanda")
            .getProperty(AirtelHttpCaller.CALL_FAILURE));
        assertEquals(200, status(call(AirtelEndpoint.COLLECTION, "zambia")));
    }

    @Test
    void eachEndpointHasItsOwnCircuit() {
        singleAttemptWithSmallWindow();
        stub(503);
        call(AirtelEndpoint.COLLECTION, "rwanda");
        call(AirtelEndpoint.COLLECTION, "rwanda");
        airtel.resetAll();
        stub(200);

        assertEquals(200, status(call(AirtelEndpoint.TRANSACTION_STATUS, "rwanda")));
    }

    @Test
    void eachHostHasItsOwnCircuit() {
        singleAttemptWithSmallWindow();
        stub(503);
        call(AirtelEndpoint.COLLECTION, "rwanda");
        call(AirtelEndpoint.COLLECTION, "rwanda");
        airtel.resetAll();
        stub(200);

        assertEquals("circuit_open", call(AirtelEndpoint.COLLECTION, "rwanda")
            .getProperty(AirtelHttpCaller.CALL_FAILURE));
        Exchange otherHost = newExchange("rwanda");
        caller.call(AirtelEndpoint.COLLECTION,
            "http://127.0.0.1:" + airtel.port() + PATH + "?throwExceptionOnFailure=false",
            otherHost);
        assertEquals(200, status(otherHost));
    }

    @Test
    void countryFallsBackToTheDefaultTenant() {
        stub(200);
        Exchange exchange = newExchange(null);

        caller.call(AirtelEndpoint.COLLECTION, uri(), exchange);

        assertEquals(1.0, circuitState("airtel-malawi-collection-localhost", "closed"));
    }

    @Test
    void hostIsTakenFromTheUri() {
        assertEquals("openapi.airtel.co.rw",
            AirtelHttpCaller.hostOf("https://openapi.airtel.co.rw:443/merchant?x=1"));
        assertEquals("openapi.airtel.co.rw",
            AirtelHttpCaller.hostOf("https://openapi.airtel.co.rw"));
        assertEquals("unknown", AirtelHttpCaller.hostOf("direct:airtel"));
    }

    @Test
    void callThatCouldNotFinishBeforeTheJobDeadlineIsNotMade() {
        stub(200);
        properties.setJobDeadlineMargin(Duration.ofMillis(100));
        caller = newCaller();
        Exchange exchange = newExchange("rwanda");
        exchange.setProperty(AirtelHttpCaller.JOB_DEADLINE, System.currentTimeMillis() + 500);

        caller.call(AirtelEndpoint.COLLECTION, uri(), exchange);

        assertEquals(AirtelCallOutcome.NOT_SENT,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertEquals("job_deadline", exchange.getProperty(AirtelHttpCaller.CALL_FAILURE));
        assertNull(exchange.getMessage().getHeader("Authorization"),
            "the request must not be left on the message");
        airtel.verify(0, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void retryThatCouldOverrunTheJobDeadlineIsNotMade() {
        stub(503);
        properties.setJobDeadlineMargin(Duration.ofMillis(100));
        properties.getTransactionStatus().getRetry().setInitialBackoff(Duration.ofMillis(300));
        caller = newCaller();
        Exchange exchange = newExchange("rwanda");
        // Room for one attempt (2 x 500ms connect + 300ms read) plus 50ms; the first retry waits
        // at least 150ms.
        exchange.setProperty(AirtelHttpCaller.JOB_DEADLINE,
            System.currentTimeMillis() + 100 + 1300 + 50);

        caller.call(AirtelEndpoint.TRANSACTION_STATUS, uri(), exchange);

        assertEquals(AirtelCallOutcome.RESPONDED,
            exchange.getProperty(AirtelHttpCaller.CALL_OUTCOME));
        assertEquals("status:503", exchange.getProperty(AirtelHttpCaller.CALL_FAILURE));
        assertEquals(503, status(exchange));
        airtel.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void clientErrorsDoNotOpenTheCircuit() {
        singleAttemptWithSmallWindow();
        stub(400);
        for (int i = 0; i < 5; i++) {
            call(AirtelEndpoint.COLLECTION, "rwanda");
        }

        assertEquals(1.0, circuitState(COLLECTION, "closed"));
        airtel.verify(5, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void endpointTimeoutsAreAppliedToTheUri() {
        stub(200);
        properties.getCollection().setReadTimeout(Duration.ofSeconds(7));
        caller = newCaller();

        call(AirtelEndpoint.COLLECTION, "rwanda");

        assertNotNull(camelContext.hasEndpoint(uri() + "&httpClient.connectTimeout=500"
            + "&httpClient.connectionRequestTimeout=500&httpClient.socketTimeout=7000"));
    }

    @Test
    void uriWithoutQueryStillGetsTheTimeouts() {
        stub(200);

        Exchange exchange = newExchange("rwanda");
        caller.call(AirtelEndpoint.TOKEN, "http://localhost:" + airtel.port() + PATH, exchange);

        assertEquals(200, status(exchange));
    }

    @Test
    void failuresBeforeTheRequestLeftAreNotSentAndRetried() {
        AirtelResilienceProperties.EndpointSettings settings = properties.getCollection();
        List<Exception> neverSent = List.of(new ConnectException(), new ConnectTimeoutException(),
            new UnknownHostException(), new NoRouteToHostException(),
            new SSLHandshakeException("handshake"), new IOException(new ConnectException()));

        for (Exception exception : neverSent) {
            AirtelHttpCaller.FailedAttempt attempt = AirtelHttpCaller.classify(exception, settings);
            assertEquals(AirtelCallOutcome.NOT_SENT, attempt.outcome, exception.toString());
            assertTrue(attempt.retryable, exception.toString());
        }
    }

    @Test
    void otherNetworkFailuresHaveAnUnknownOutcome() {
        AirtelHttpCaller.FailedAttempt attempt = AirtelHttpCaller.classify(
            new SocketTimeoutException(), properties.getTransactionStatus());

        assertEquals(AirtelCallOutcome.UNKNOWN, attempt.outcome);
        assertTrue(attempt.retryable);
        assertEquals("no_response:SocketTimeoutException", attempt.getMessage());
    }

    @Test
    void localErrorsAreNeitherRetriedNorCountedAgainstTheCircuit() {
        AirtelResilienceProperties.EndpointSettings settings = properties.getCollection();
        AirtelHttpCaller.FailedAttempt attempt =
            AirtelHttpCaller.classify(new IllegalStateException(), settings);
        CircuitBreakerConfig circuitBreaker = AirtelHttpCaller.circuitBreakerConfig(settings);
        final RetryConfig retry = AirtelHttpCaller.retryConfig(settings);

        assertEquals(AirtelCallOutcome.NOT_SENT, attempt.outcome);
        assertFalse(attempt.retryable);
        assertFalse(circuitBreaker.getRecordExceptionPredicate().test(attempt));
        assertTrue(circuitBreaker.getIgnoreExceptionPredicate().test(attempt));
        assertFalse(retry.getExceptionPredicate().test(attempt));
        assertFalse(circuitBreaker.getRecordExceptionPredicate()
            .test(new IllegalStateException()));
        assertFalse(retry.getExceptionPredicate().test(new IllegalStateException()));
    }

    @Test
    void reasonsNameOnlyTheFailureType() {
        assertEquals("none", AirtelHttpCaller.reason(null));
        assertEquals("IOException",
            AirtelHttpCaller.reason(new IOException("http://host/path?secret")));
        assertEquals("status:503", AirtelHttpCaller.reason(new AirtelHttpCaller.FailedAttempt(
            AirtelCallOutcome.RESPONDED, "status:503", true, true)));
    }

    @Test
    void emptyRetryStatusListIsAllowed() {
        AirtelResilienceProperties.Retry retry = new AirtelResilienceProperties.Retry();

        retry.setRetryStatusCodes(null);

        assertTrue(retry.getRetryStatusCodes().isEmpty());
    }

    private static void waitForOpenStateToEnd() {
        await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(2)).until(() -> true);
    }

    private void singleAttemptWithSmallWindow() {
        for (AirtelEndpoint endpoint : AirtelEndpoint.values()) {
            AirtelResilienceProperties.EndpointSettings settings = properties.settingsFor(endpoint);
            settings.getRetry().setMaxAttempts(1);
            settings.getCircuitBreaker().setSlidingWindowSize(4);
            settings.getCircuitBreaker().setMinimumNumberOfCalls(2);
        }
        caller = newCaller();
    }

    private AirtelHttpCaller newCaller() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("meterRegistry", meterRegistry);
        return new AirtelHttpCaller(producerTemplate, properties, airtelUtils,
            beans.getBeanProvider(MeterRegistry.class));
    }

    private void stub(int status) {
        airtel.stubFor(post(PATH).willReturn(aResponse().withStatus(status)
            .withBody("{\"status\":\"ok\"}")));
    }

    private String uri() {
        return "http://localhost:" + airtel.port() + PATH + "?throwExceptionOnFailure=false";
    }

    private Exchange call(AirtelEndpoint endpoint, String country) {
        Exchange exchange = newExchange(country);
        caller.call(endpoint, uri(), exchange);
        return exchange;
    }

    private Exchange newExchange(String country) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(PLATFORM_TENANT_ID, country);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getMessage().setHeader("Authorization", "Bearer secret-token");
        exchange.getMessage().setBody("{\"amount\":\"100\"}");
        return exchange;
    }

    private static Integer status(Exchange exchange) {
        return exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
    }

    private double circuitState(String name, String state) {
        return meterRegistry.get("resilience4j.circuitbreaker.state").tag("name", name)
            .tag("state", state).gauge().value();
    }
}
