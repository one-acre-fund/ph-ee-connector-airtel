package org.mifos.connector.airtel.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    private final Map<String, TimedEntry> workflowInstanceStore = new ConcurrentHashMap<>();
    private final String keyPrefix;
    private final long workflowTtlMillis;

    public InMemoryPaybillStateStore(RedisStoreProperties redisStoreProperties) {
        this.keyPrefix = redisStoreProperties.getKeyPrefix();
        this.workflowTtlMillis = TimeUnit.SECONDS.toMillis(
                redisStoreProperties.getTtl().getPaybillWorkflowSeconds());
    }

    @PostConstruct
    void logStoreBackend() {
        log.info("Paybill workflow correlation store backend: memory (ttlMs={})", workflowTtlMillis);
    }

    @Override
    public void putWorkflowInstance(String txnId, String workflowInstanceKey) {
        long expiresAtMillis = System.currentTimeMillis() + workflowTtlMillis;
        workflowInstanceStore.put(workflowKey(txnId), new TimedEntry(workflowInstanceKey, expiresAtMillis));
    }

    @Override
    public String getWorkflowInstance(String txnId) {
        String key = workflowKey(txnId);
        TimedEntry entry = workflowInstanceStore.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired()) {
                return null;
            }
            return existing;
        });
        return entry != null ? entry.value() : null;
    }

    @Override
    public void removeWorkflowInstance(String txnId) {
        workflowInstanceStore.remove(workflowKey(txnId));
    }

    @Override
    public String consumeWorkflowInstance(String txnId) {
        TimedEntry removed = workflowInstanceStore.remove(workflowKey(txnId));
        if (removed == null || removed.isExpired()) {
            return null;
        }
        return removed.value();
    }

    private String workflowKey(String txnId) {
        return keyPrefix + ":" + WORKFLOW_KEY_PREFIX + txnId;
    }

    private static final class TimedEntry {
        private final String value;
        private final long expiresAtMillis;

        private TimedEntry(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        private String value() {
            return value;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
