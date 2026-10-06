package org.mifos.connector.airtel.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import org.mifos.connector.airtel.config.RedisStoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${airtel-connector.redis.type:redis}'.equalsIgnoreCase('memory')")
public class InMemoryPaybillStateStore implements PaybillStateStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPaybillStateStore.class);
    private static final String WORKFLOW_KEY_PREFIX = "paybill:workflow:";

    private final Map<String, String> workflowInstanceStore = new ConcurrentHashMap<>();
    private final String keyPrefix;

    public InMemoryPaybillStateStore(RedisStoreProperties redisStoreProperties) {
        this.keyPrefix = redisStoreProperties.getKeyPrefix();
    }

    @PostConstruct
    void logStoreBackend() {
        log.info("Paybill workflow correlation store backend: memory");
    }

    @Override
    public void putWorkflowInstance(String txnId, String workflowInstanceKey) {
        workflowInstanceStore.put(workflowKey(txnId), workflowInstanceKey);
    }

    @Override
    public String getWorkflowInstance(String txnId) {
        return workflowInstanceStore.get(workflowKey(txnId));
    }

    @Override
    public void removeWorkflowInstance(String txnId) {
        workflowInstanceStore.remove(workflowKey(txnId));
    }

    private String workflowKey(String txnId) {
        return keyPrefix + ":" + WORKFLOW_KEY_PREFIX + txnId;
    }
}
