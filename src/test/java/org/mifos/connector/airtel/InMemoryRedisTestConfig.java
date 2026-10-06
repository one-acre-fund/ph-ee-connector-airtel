package org.mifos.connector.airtel;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Provides a ConcurrentHashMap-backed {@link StringRedisTemplate} for Spring tests
 * so the context loads without a live Redis server.
 */
@TestConfiguration
public class InMemoryRedisTestConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        Map<String, TimedValue> store = new ConcurrentHashMap<>();
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class, Mockito.withSettings().lenient());
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class, Mockito.withSettings().lenient());
        when(template.opsForValue()).thenReturn(ops);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            long ttl = invocation.getArgument(2);
            TimeUnit unit = invocation.getArgument(3);
            store.put(key, new TimedValue(value, System.currentTimeMillis() + unit.toMillis(ttl)));
            return null;
        }).when(ops).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            Duration duration = invocation.getArgument(2);
            store.put(key, new TimedValue(value, System.currentTimeMillis() + duration.toMillis()));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));

        when(ops.get(anyString())).thenAnswer(invocation -> {
            TimedValue timedValue = liveValue(store, invocation.getArgument(0));
            return timedValue != null ? timedValue.value : null;
        });

        when(ops.getAndDelete(anyString())).thenAnswer(invocation -> {
            TimedValue timedValue = liveValue(store, invocation.getArgument(0));
            if (timedValue == null) {
                return null;
            }
            store.remove(invocation.getArgument(0));
            return timedValue.value;
        });

        when(template.hasKey(anyString())).thenAnswer(invocation ->
                liveValue(store, invocation.getArgument(0)) != null);

        when(template.delete(anyString())).thenAnswer(invocation ->
                store.remove(invocation.getArgument(0)) != null);

        when(template.getExpire(anyString(), any(TimeUnit.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            TimeUnit unit = invocation.getArgument(1);
            TimedValue timedValue = liveValue(store, key);
            if (timedValue == null) {
                return -2L;
            }
            long remainingMillis = timedValue.expiresAtMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                store.remove(key);
                return -2L;
            }
            return unit.convert(remainingMillis, TimeUnit.MILLISECONDS);
        });

        return template;
    }

    private static TimedValue liveValue(Map<String, TimedValue> store, String key) {
        TimedValue timedValue = store.get(key);
        if (timedValue == null) {
            return null;
        }
        if (System.currentTimeMillis() >= timedValue.expiresAtMillis) {
            store.remove(key);
            return null;
        }
        return timedValue;
    }

    private static final class TimedValue {
        private final String value;
        private final long expiresAtMillis;

        private TimedValue(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
