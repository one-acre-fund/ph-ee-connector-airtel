package org.mifos.connector.airtel.store;

import java.time.Duration;
import javax.annotation.PostConstruct;
import org.mifos.connector.airtel.config.RedisStoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${airtel-connector.redis.type:redis}'.equalsIgnoreCase('memory')")
public class RedisPaybillStateStore implements PaybillStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPaybillStateStore.class);
    private static final String WORKFLOW_KEY_PREFIX = "paybill:workflow:";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final long workflowTtlSeconds;

    public RedisPaybillStateStore(StringRedisTemplate redisTemplate, RedisStoreProperties redisStoreProperties) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = redisStoreProperties.getKeyPrefix();
        this.workflowTtlSeconds = redisStoreProperties.getTtl().getPaybillWorkflowSeconds();
    }

    @PostConstruct
    void logStoreBackend() {
        log.info("Paybill workflow correlation store backend: redis");
    }

    @Override
    public void putWorkflowInstance(String txnId, String workflowInstanceKey) {
        redisTemplate.opsForValue().set(workflowKey(txnId), workflowInstanceKey, Duration.ofSeconds(workflowTtlSeconds));
    }

    @Override
    public String getWorkflowInstance(String txnId) {
        return redisTemplate.opsForValue().get(workflowKey(txnId));
    }

    @Override
    public void removeWorkflowInstance(String txnId) {
        redisTemplate.delete(workflowKey(txnId));
    }

    @Override
    public String consumeWorkflowInstance(String txnId) {
        return redisTemplate.opsForValue().getAndDelete(workflowKey(txnId));
    }

    private String workflowKey(String txnId) {
        return keyPrefix + ":" + WORKFLOW_KEY_PREFIX + txnId;
    }
}
