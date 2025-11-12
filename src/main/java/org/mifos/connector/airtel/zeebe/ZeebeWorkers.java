package org.mifos.connector.airtel.zeebe;

import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_REQUEST_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_RESPONSE_BODY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COLLECTION_TRANSACTION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.airtel.camel.config.CamelProperties.COUNTRY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.CURRENCY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.DEPLOYED_PROCESS;
import static org.mifos.connector.airtel.camel.config.CamelProperties.PLATFORM_TENANT_ID;
import static org.mifos.connector.airtel.camel.routes.PaybillRouteBuilder.workflowInstanceStore;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CHANNEL_REQUEST;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CLIENT_CORRELATION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_CODE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_DESCRIPTION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ERROR_INFORMATION;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.GET_TRANSACTION_STATUS_WORKER_NAME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INIT_TRANSFER_WORKER_NAME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.SERVER_TRANSACTION_STATUS_RETRY_COUNT;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TIMER;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSFER_CREATE_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSFER_MESSAGE;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.ZEEBE_ELEMENT_INSTANCE_KEY;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.json.JSONObject;
import org.mifos.connector.airtel.dto.CollectionRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Contains workers that will run based on the BPMN flow.
 */
@Component
public class ZeebeWorkers {

    private static final Logger logger = LoggerFactory.getLogger(ZeebeWorkers.class);
    private final ProducerTemplate producerTemplate;
    private final ZeebeClient zeebeClient;

    private final CamelContext camelContext;

    /**
     * Determines if an actual call to Airtel API will be made or not.
     */
    @Value("${skip.enabled}")
    private boolean skipAirtelMoney;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @Value("#{${countryCodes}}")
    private Map<String, String> countryCodes;

    @Value("${transaction-id-prefix}")
    private String transactionIdPrefix;

    /**
     * Creates an instance of {@link ZeebeWorkers} with all required params.
     *
     * @param producerTemplate {@link ProducerTemplate}
     * @param zeebeClient      {@link ZeebeClient}
     * @param camelContext     {@link CamelContext}
     */
    public ZeebeWorkers(ProducerTemplate producerTemplate, ZeebeClient zeebeClient,
                        CamelContext camelContext) {
        this.producerTemplate = producerTemplate;
        this.zeebeClient = zeebeClient;
        this.camelContext = camelContext;
    }

    /**
     * Sets up the necessary Zeebe workers for airtel money transactions.
     */
    @PostConstruct
    public void setupWorkers() {
        zeebeClient.newWorker()
            .jobType(INIT_TRANSFER_WORKER_NAME)
            .handler((client, job) -> {
                logger.info("Job '{}' started from process '{}' with key {}", job.getType(),
                    job.getBpmnProcessId(), job.getKey());

                Map<String, Object> variables = job.getVariablesAsMap();
                if (skipAirtelMoney) {
                    logger.info("Skipping Airtel Money");
                    variables.put(TRANSACTION_FAILED, false);
                    variables.put(TRANSFER_CREATE_FAILED, false);
                } else {
                    JSONObject channelRequest =
                        new JSONObject((String) variables.get(CHANNEL_REQUEST));
                    String transactionId = (String) variables.get(TRANSACTION_ID);

                    CollectionRequestDto collectionRequestDto = CollectionRequestDto
                        .fromChannelRequest(channelRequest, transactionId, countryCodes,
                            transactionIdPrefix);
                    logger.info(collectionRequestDto.toString());
                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(COLLECTION_REQUEST_BODY, collectionRequestDto);
                    exchange.setProperty(CORRELATION_ID, transactionId);
                    exchange.setProperty(DEPLOYED_PROCESS, job.getBpmnProcessId());
                    exchange.setProperty(COUNTRY, collectionRequestDto.getTransaction()
                        .getCountry());
                    exchange.setProperty(CURRENCY, collectionRequestDto.getTransaction()
                        .getCurrency());
                    exchange.setProperty(PLATFORM_TENANT_ID, variables.get(TENANT_ID));

                    variables.put(COLLECTION_REQUEST_BODY, collectionRequestDto.toString());

                    producerTemplate.send("direct:collection-request-base", exchange);
                    variables.put(COLLECTION_RESPONSE_BODY,
                        exchange.getProperty(COLLECTION_RESPONSE_BODY));

                    boolean isTransactionFailed = exchange
                        .getProperty(TRANSACTION_FAILED, boolean.class);
                    if (isTransactionFailed) {
                        variables.put(TRANSACTION_FAILED, true);
                        variables.put(TRANSFER_CREATE_FAILED, true);
                        String errorBody = exchange.getProperty(ERROR_INFORMATION, String.class);
                        variables.put(ERROR_INFORMATION, errorBody);
                        variables.put(ERROR_CODE, exchange.getProperty(ERROR_CODE, String.class));
                        variables.put(ERROR_DESCRIPTION,
                            exchange.getProperty(ERROR_DESCRIPTION, String.class));
                    } else {
                        variables.put(TRANSACTION_FAILED, false);
                        variables.put(TRANSFER_CREATE_FAILED, false);
                    }
                }

                client.newCompleteCommand(job.getKey())
                    .variables(variables)
                    .send()
                    .join();
            })
            .name(INIT_TRANSFER_WORKER_NAME)
            .maxJobsActive(workerMaxJobs)
            .open();

        zeebeClient.newWorker()
            .jobType(GET_TRANSACTION_STATUS_WORKER_NAME)
            .handler((client, job) -> {
                logger.info("Job '{}' started from process '{}' with key {}", job.getType(),
                    job.getBpmnProcessId(), job.getKey());

                Map<String, Object> variables = job.getVariablesAsMap();
                Integer retryCount = 1 + (Integer) variables
                    .getOrDefault(SERVER_TRANSACTION_STATUS_RETRY_COUNT, 0);
                variables.put(SERVER_TRANSACTION_STATUS_RETRY_COUNT, retryCount);
                String transactionId = (String) variables.get(TRANSACTION_ID);
                if (skipAirtelMoney) {
                    logger.info("Skipping Airtel Money...");
                    variables.put(TRANSACTION_FAILED, false);
                    variables.put(TRANSFER_CREATE_FAILED, false);
                    zeebeClient.newPublishMessageCommand()
                        .messageName(TRANSFER_MESSAGE)
                        .correlationKey(transactionId)
                        .timeToLive(Duration.ofMillis(300))
                        .variables(variables)
                        .send()
                        .join();
                } else {
                    logger.info("Trying count: {}", retryCount);
                    JSONObject channelRequest =
                        new JSONObject((String) variables.get(CHANNEL_REQUEST));
                    CollectionRequestDto collectionRequestDto = CollectionRequestDto
                        .fromChannelRequest(channelRequest, transactionId, countryCodes,
                            transactionIdPrefix);
                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, transactionId);
                    exchange.setProperty(TRANSACTION_ID, transactionId);
                    exchange.setProperty(COLLECTION_TRANSACTION_ID,
                        collectionRequestDto.getTransaction().getId());
                    exchange.setProperty(COLLECTION_REQUEST_BODY, collectionRequestDto);
                    exchange.setProperty(SERVER_TRANSACTION_STATUS_RETRY_COUNT, retryCount);
                    exchange.setProperty(ZEEBE_ELEMENT_INSTANCE_KEY, job.getElementInstanceKey());
                    exchange.setProperty(TIMER, variables.get(TIMER));
                    exchange.setProperty(DEPLOYED_PROCESS, job.getBpmnProcessId());
                    exchange.setProperty(COUNTRY, collectionRequestDto.getTransaction()
                        .getCountry());
                    exchange.setProperty(CURRENCY, collectionRequestDto.getTransaction()
                        .getCurrency());
                    producerTemplate.send("direct:get-transaction-status-base", exchange);
                }
                client.newCompleteCommand(job.getKey())
                    .send()
                    .join();
            })
            .name(GET_TRANSACTION_STATUS_WORKER_NAME)
            .maxJobsActive(workerMaxJobs)
            .open();

        zeebeClient.newWorker()
            .jobType("delete-airtel-workflow-instancekey")
            .handler(((client, job) -> {
                Map<String, Object> variables = job.getVariablesAsMap();
                String transactionId = (String) variables.get(CLIENT_CORRELATION_ID);
                logger.info("Removing Airtel txn id {} & instance key from store", transactionId);
                workflowInstanceStore.remove(transactionId);
                client.newCompleteCommand(job.getKey())
                    .send()
                    .join();
            }))
            .name("Cleanup")
            .maxJobsActive(workerMaxJobs)
            .open();
    }
}
