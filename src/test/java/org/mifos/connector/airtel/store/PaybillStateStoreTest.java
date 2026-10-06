package org.mifos.connector.airtel.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.connector.airtel.config.RedisStoreProperties;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaybillStateStoreTest {

    private static final String KEY_PREFIX = "test-prefix";
    private static final String AIRTEL_TXN_ID = "txn-123";
    private static final String OTHER_TXN_ID = "txn-999";
    private static final long WORKFLOW_TTL_SECONDS = 172800;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis put stores value with configured TTL and prefixed key")
    void redisPut_shouldStoreWorkflowKeyWithTtl() {
        RedisPaybillStateStore store = redisStore();
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-456");
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(workflowKey(AIRTEL_TXN_ID)),
                org.mockito.ArgumentMatchers.eq("workflow-456"), ttlCaptor.capture());
        assertEquals(WORKFLOW_TTL_SECONDS, ttlCaptor.getValue().getSeconds());
    }

    @Test
    @DisplayName("Redis put uses custom TTL from properties")
    void redisPut_shouldUseCustomTtl() {
        RedisStoreProperties properties = redisProperties();
        properties.getTtl().setPaybillWorkflowSeconds(60);
        RedisPaybillStateStore store = new RedisPaybillStateStore(redisTemplate, properties);
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-456");
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(workflowKey(AIRTEL_TXN_ID)),
                org.mockito.ArgumentMatchers.eq("workflow-456"), ttlCaptor.capture());
        assertEquals(60, ttlCaptor.getValue().getSeconds());
    }

    @Test
    @DisplayName("Redis get returns stored workflow instance")
    void redisGet_shouldReturnStoredValue() {
        when(valueOperations.get(workflowKey(AIRTEL_TXN_ID))).thenReturn("workflow-456");
        assertEquals("workflow-456", redisStore().getWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Redis get returns null when key missing")
    void redisGet_shouldReturnNullWhenMissing() {
        when(valueOperations.get(workflowKey(AIRTEL_TXN_ID))).thenReturn(null);
        assertNull(redisStore().getWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Redis remove deletes prefixed key")
    void redisRemove_shouldDeleteKey() {
        redisStore().removeWorkflowInstance(AIRTEL_TXN_ID);
        verify(redisTemplate).delete(workflowKey(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Redis consume atomically get-and-deletes prefixed key")
    void redisConsume_shouldGetAndDelete() {
        when(valueOperations.getAndDelete(workflowKey(AIRTEL_TXN_ID))).thenReturn("workflow-456");

        assertEquals("workflow-456", redisStore().consumeWorkflowInstance(AIRTEL_TXN_ID));
        verify(valueOperations).getAndDelete(workflowKey(AIRTEL_TXN_ID));
        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Redis consume returns null when key missing")
    void redisConsume_shouldReturnNullWhenMissing() {
        when(valueOperations.getAndDelete(workflowKey(AIRTEL_TXN_ID))).thenReturn(null);

        assertNull(redisStore().consumeWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Redis put is invoked once per call")
    void redisPut_shouldCallRedisOncePerPut() {
        RedisPaybillStateStore store = redisStore();
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-1");
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-2");
        verify(valueOperations, times(2)).set(org.mockito.ArgumentMatchers.eq(workflowKey(AIRTEL_TXN_ID)),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    @DisplayName("Memory store put/get/remove works without touching Redis")
    void memoryStore_shouldPutGetAndRemoveWithoutRedis() {
        InMemoryPaybillStateStore store = memoryStore();
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-456");
        assertEquals("workflow-456", store.getWorkflowInstance(AIRTEL_TXN_ID));
        verify(redisTemplate, never()).opsForValue();
        verifyNoInteractions(valueOperations);
        store.removeWorkflowInstance(AIRTEL_TXN_ID);
        assertNull(store.getWorkflowInstance(AIRTEL_TXN_ID));
        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Memory consume returns value once then null for concurrent second call")
    void memoryStore_consume_shouldBeAtomic() {
        InMemoryPaybillStateStore store = memoryStore();
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-456");

        assertEquals("workflow-456", store.consumeWorkflowInstance(AIRTEL_TXN_ID));
        assertNull(store.consumeWorkflowInstance(AIRTEL_TXN_ID));
        assertNull(store.getWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Memory store expires entries after paybillWorkflowSeconds")
    void memoryStore_shouldEvictExpiredEntries() throws Exception {
        RedisStoreProperties properties = redisProperties();
        properties.getTtl().setPaybillWorkflowSeconds(1);
        InMemoryPaybillStateStore store = new InMemoryPaybillStateStore(properties);
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-456");
        assertEquals("workflow-456", store.getWorkflowInstance(AIRTEL_TXN_ID));

        Thread.sleep(1100);

        assertNull(store.getWorkflowInstance(AIRTEL_TXN_ID));
        assertNull(store.consumeWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Memory store overwrites existing correlation mapping")
    void memoryStore_shouldOverwriteExistingValue() {
        InMemoryPaybillStateStore store = memoryStore();
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-old");
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-new");
        assertEquals("workflow-new", store.getWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Memory store keeps mappings isolated per transaction id")
    void memoryStore_shouldIsolateKeys() {
        InMemoryPaybillStateStore store = memoryStore();
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-a");
        store.putWorkflowInstance(OTHER_TXN_ID, "workflow-b");
        assertEquals("workflow-a", store.getWorkflowInstance(AIRTEL_TXN_ID));
        assertEquals("workflow-b", store.getWorkflowInstance(OTHER_TXN_ID));
        store.removeWorkflowInstance(AIRTEL_TXN_ID);
        assertNull(store.getWorkflowInstance(AIRTEL_TXN_ID));
        assertEquals("workflow-b", store.getWorkflowInstance(OTHER_TXN_ID));
    }

    @Test
    @DisplayName("Memory store returns null for unknown transaction id")
    void memoryStore_shouldReturnNullWhenMissing() {
        assertNull(memoryStore().getWorkflowInstance(AIRTEL_TXN_ID));
    }

    @Test
    @DisplayName("Both backends can log store type on startup")
    void logStoreBackend_shouldRunForBothBackends() {
        redisStore().logStoreBackend();
        memoryStore().logStoreBackend();
    }

    @Test
    @DisplayName("Memory remove of unknown key is a no-op")
    void memoryStore_removeUnknownKey_shouldBeNoOp() {
        InMemoryPaybillStateStore store = memoryStore();
        store.removeWorkflowInstance("missing");
        assertNull(store.getWorkflowInstance("missing"));
    }

    @Test
    @DisplayName("Empty key prefix still builds workflow keys")
    void redisStore_shouldWorkWithEmptyKeyPrefix() {
        RedisStoreProperties properties = new RedisStoreProperties();
        properties.setKeyPrefix("");
        properties.getTtl().setPaybillWorkflowSeconds(30);
        RedisPaybillStateStore store = new RedisPaybillStateStore(redisTemplate, properties);
        store.putWorkflowInstance(AIRTEL_TXN_ID, "workflow-456");
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(":paybill:workflow:" + AIRTEL_TXN_ID),
                org.mockito.ArgumentMatchers.eq("workflow-456"), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    private RedisPaybillStateStore redisStore() {
        return new RedisPaybillStateStore(redisTemplate, redisProperties());
    }

    private InMemoryPaybillStateStore memoryStore() {
        return new InMemoryPaybillStateStore(redisProperties());
    }

    private RedisStoreProperties redisProperties() {
        RedisStoreProperties properties = new RedisStoreProperties();
        properties.setKeyPrefix(KEY_PREFIX);
        RedisStoreProperties.Ttl ttl = new RedisStoreProperties.Ttl();
        ttl.setPaybillWorkflowSeconds(WORKFLOW_TTL_SECONDS);
        properties.setTtl(ttl);
        return properties;
    }

    private String workflowKey(String txnId) {
        return KEY_PREFIX + ":paybill:workflow:" + txnId;
    }
}
