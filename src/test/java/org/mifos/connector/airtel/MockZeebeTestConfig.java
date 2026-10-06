package org.mifos.connector.airtel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Provides a no-op {@link ZeebeClient} so Camel route tests can publish messages
 * and register workers without a live Zeebe broker.
 */
@TestConfiguration
public class MockZeebeTestConfig {

    @Bean
    @Primary
    public ZeebeClient zeebeClient() {
        ZeebeClient client = mock(ZeebeClient.class, org.mockito.Mockito.withSettings().lenient());

        JobWorkerBuilderStep1 step1 = mock(JobWorkerBuilderStep1.class,
                org.mockito.Mockito.withSettings().lenient());
        JobWorkerBuilderStep1.JobWorkerBuilderStep2 step2 =
                mock(JobWorkerBuilderStep1.JobWorkerBuilderStep2.class,
                        org.mockito.Mockito.withSettings().lenient());
        JobWorkerBuilderStep1.JobWorkerBuilderStep3 step3 =
                mock(JobWorkerBuilderStep1.JobWorkerBuilderStep3.class,
                        org.mockito.Mockito.withSettings().lenient());
        when(client.newWorker()).thenReturn(step1);
        when(step1.jobType(anyString())).thenReturn(step2);
        when(step2.handler(any())).thenReturn(step3);
        when(step3.name(anyString())).thenReturn(step3);
        when(step3.maxJobsActive(any(Integer.class))).thenReturn(step3);
        when(step3.open()).thenReturn(mock(JobWorker.class));

        PublishMessageCommandStep1 publishStep1 = mock(PublishMessageCommandStep1.class,
                org.mockito.Mockito.withSettings().lenient());
        PublishMessageCommandStep1.PublishMessageCommandStep2 publishStep2 =
                mock(PublishMessageCommandStep1.PublishMessageCommandStep2.class,
                        org.mockito.Mockito.withSettings().lenient());
        PublishMessageCommandStep1.PublishMessageCommandStep3 publishStep3 =
                mock(PublishMessageCommandStep1.PublishMessageCommandStep3.class,
                        org.mockito.Mockito.withSettings().lenient());
        @SuppressWarnings("unchecked")
        ZeebeFuture<PublishMessageResponse> publishFuture = mock(ZeebeFuture.class,
                org.mockito.Mockito.withSettings().lenient());
        when(client.newPublishMessageCommand()).thenReturn(publishStep1);
        when(publishStep1.messageName(anyString())).thenReturn(publishStep2);
        when(publishStep2.correlationKey(anyString())).thenReturn(publishStep3);
        when(publishStep3.timeToLive(any(Duration.class))).thenReturn(publishStep3);
        when(publishStep3.variables(anyMap())).thenReturn(publishStep3);
        when(publishStep3.send()).thenReturn(publishFuture);
        when(publishFuture.join()).thenReturn(null);

        SetVariablesCommandStep1 setStep1 = mock(SetVariablesCommandStep1.class,
                org.mockito.Mockito.withSettings().lenient());
        SetVariablesCommandStep1.SetVariablesCommandStep2 setStep2 =
                mock(SetVariablesCommandStep1.SetVariablesCommandStep2.class,
                        org.mockito.Mockito.withSettings().lenient());
        @SuppressWarnings("unchecked")
        ZeebeFuture<SetVariablesResponse> setFuture = mock(ZeebeFuture.class,
                org.mockito.Mockito.withSettings().lenient());
        when(client.newSetVariablesCommand(anyLong())).thenReturn(setStep1);
        when(setStep1.variables(anyMap())).thenReturn(setStep2);
        when(setStep2.send()).thenReturn(setFuture);
        when(setFuture.join()).thenReturn(null);

        return client;
    }
}
