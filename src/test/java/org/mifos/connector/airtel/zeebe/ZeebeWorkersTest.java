package org.mifos.connector.airtel.zeebe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CHANNEL_REQUEST;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INIT_TRANSFER_WORKER_NAME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.GET_TRANSACTION_STATUS_WORKER_NAME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mifos.connector.airtel.util.AirtelUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for {@link ZeebeWorkers} verifying that PLATFORM_TENANT_ID is set
 * via {@link AirtelUtils#getCountryFromCurrency(String)}.
 */
class ZeebeWorkersTest {

    private ZeebeClient zeebeClient;
    private ProducerTemplate producerTemplate;
    private CamelContext camelContext;
    private AirtelUtils airtelUtils;

    private ZeebeWorkers zeebeWorkers;

    /** Captures the JobHandler lambdas registered during setupWorkers(). */
    private final Map<String, JobHandler> capturedHandlers = new HashMap<>();

    private static final String CHANNEL_REQUEST_JSON = """
            {
              "amount": { "currency": "ZMW", "amount": 100 },
              "payer": {
                "partyIdInfo": { "partyIdentifier": "+260788123456" }
              }
            }
            """;

    @BeforeEach
    void setUp() {
        zeebeClient = mock(ZeebeClient.class);
        producerTemplate = mock(ProducerTemplate.class);
        camelContext = new DefaultCamelContext();
        airtelUtils = mock(AirtelUtils.class);

        // Stub fluent Zeebe worker builder — capture each handler by its jobType
        JobWorkerBuilderStep1 step1 = mock(JobWorkerBuilderStep1.class);
        JobWorkerBuilderStep2 step2 = mock(JobWorkerBuilderStep2.class);
        JobWorkerBuilderStep3 step3 = mock(JobWorkerBuilderStep3.class);
        JobWorker jobWorker = mock(JobWorker.class);

        when(zeebeClient.newWorker()).thenReturn(step1);
        when(step1.jobType(anyString())).thenReturn(step2);
        ArgumentCaptor<JobHandler> handlerCaptor = ArgumentCaptor.forClass(JobHandler.class);
        when(step2.handler(handlerCaptor.capture())).thenReturn(step3);
        when(step3.name(anyString())).thenReturn(step3);
        when(step3.maxJobsActive(anyInt())).thenReturn(step3);
        when(step3.open()).thenReturn(jobWorker);

        Map<String, String> countryCodes = Map.of("zmw", "zambia", "rwf", "rwanda");

        zeebeWorkers = new ZeebeWorkers(producerTemplate, zeebeClient, camelContext);
        ReflectionTestUtils.setField(zeebeWorkers, "airtelUtils", airtelUtils);
        ReflectionTestUtils.setField(zeebeWorkers, "skipAirtelMoney", false);
        ReflectionTestUtils.setField(zeebeWorkers, "workerMaxJobs", 1);
        ReflectionTestUtils.setField(zeebeWorkers, "countryCodes", countryCodes);
        ReflectionTestUtils.setField(zeebeWorkers, "transactionIdPrefix", "");

        // Call @PostConstruct manually
        zeebeWorkers.setupWorkers();

        // Three workers are registered; the captor will have captured handlers in order.
        // 1st = init-transfer, 2nd = get-transaction-status, 3rd = delete-workflow
        var allHandlers = handlerCaptor.getAllValues();
        capturedHandlers.put(INIT_TRANSFER_WORKER_NAME, allHandlers.get(0));
        capturedHandlers.put(GET_TRANSACTION_STATUS_WORKER_NAME, allHandlers.get(1));
    }

    @DisplayName("init-transfer worker sets PLATFORM_TENANT_ID from airtelUtils.getCountryFromCurrency (line 117)")
    @Test
    void initTransferWorker_setsPlatformTenantId() throws Exception {
        when(airtelUtils.getCountryFromCurrency("ZMW")).thenReturn("zambia");

        ActivatedJob job = mockActivatedJob(Map.of(
                CHANNEL_REQUEST, CHANNEL_REQUEST_JSON,
                TRANSACTION_ID, "txn-001"
        ));

        JobClient jobClient = mockJobClient();

        // Capture the exchange sent to the producer template
        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        when(producerTemplate.send(eq("direct:collection-request-base"),
                exchangeCaptor.capture())).thenAnswer(inv -> {
            Exchange ex = inv.getArgument(1);
            // Simulate a successful response so the handler doesn't NPE
            ex.setProperty("transactionFailed", false);
            return ex;
        });

        // Execute the captured handler
        capturedHandlers.get(INIT_TRANSFER_WORKER_NAME).handle(jobClient, job);

        // Verify PLATFORM_TENANT_ID was set from getCountryFromCurrency
        verify(airtelUtils).getCountryFromCurrency("ZMW");
        Exchange sentExchange = exchangeCaptor.getValue();
        assertEquals("zambia", sentExchange.getProperty(PLATFORM_TENANT_ID));
    }

    @DisplayName("get-transaction-status worker sets PLATFORM_TENANT_ID from airtelUtils.getCountryFromCurrency (line 194)")
    @Test
    void getTransactionStatusWorker_setsPlatformTenantId() throws Exception {
        when(airtelUtils.getCountryFromCurrency("ZMW")).thenReturn("zambia");

        ActivatedJob job = mockActivatedJob(Map.of(
                CHANNEL_REQUEST, CHANNEL_REQUEST_JSON,
                TRANSACTION_ID, "txn-002"
        ));

        JobClient jobClient = mockJobClient();

        // Capture exchange sent to producer template
        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        when(producerTemplate.send(eq("direct:get-transaction-status-base"),
                exchangeCaptor.capture())).thenAnswer(inv -> inv.getArgument(1));

        // Execute the captured handler
        capturedHandlers.get(GET_TRANSACTION_STATUS_WORKER_NAME).handle(jobClient, job);

        // Verify PLATFORM_TENANT_ID was set from getCountryFromCurrency
        verify(airtelUtils).getCountryFromCurrency("ZMW");
        Exchange sentExchange = exchangeCaptor.getValue();
        assertEquals("zambia", sentExchange.getProperty(PLATFORM_TENANT_ID));
    }

    // ----- helpers -------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ActivatedJob mockActivatedJob(Map<String, Object> variables) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(new HashMap<>(variables));
        when(job.getBpmnProcessId()).thenReturn("test-process");
        when(job.getKey()).thenReturn(123L);
        when(job.getElementInstanceKey()).thenReturn(456L);
        when(job.getType()).thenReturn("test-job-type");
        return job;
    }

    @SuppressWarnings("unchecked")
    private JobClient mockJobClient() {
        JobClient jobClient = mock(JobClient.class);
        CompleteJobCommandStep1 completeStep = mock(CompleteJobCommandStep1.class);
        ZeebeFuture<CompleteJobResponse> future = mock(ZeebeFuture.class);

        when(jobClient.newCompleteCommand(any(Long.class))).thenReturn(completeStep);
        when(completeStep.variables(any(Map.class))).thenReturn(completeStep);
        when(completeStep.send()).thenReturn(future);
        when(future.join()).thenReturn(null);
        return jobClient;
    }
}
