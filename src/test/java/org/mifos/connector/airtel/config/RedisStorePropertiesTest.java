package org.mifos.connector.airtel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RedisStorePropertiesTest {

    @Test
    void defaults_shouldProvideExpectedValues() {
        RedisStoreProperties properties = new RedisStoreProperties();

        assertEquals("redis", properties.getType());
        assertFalse(properties.isMemoryStore());
        assertEquals("airtel-connector", properties.getKeyPrefix());
        assertEquals(172800, properties.getTtl().getPaybillWorkflowSeconds());
    }

    @Test
    void setters_shouldUpdateConfiguration() {
        RedisStoreProperties properties = new RedisStoreProperties();
        RedisStoreProperties.Ttl ttl = new RedisStoreProperties.Ttl();
        ttl.setPaybillWorkflowSeconds(300);

        properties.setType("memory");
        properties.setKeyPrefix("custom-prefix");
        properties.setTtl(ttl);

        assertEquals("memory", properties.getType());
        assertTrue(properties.isMemoryStore());
        assertEquals("custom-prefix", properties.getKeyPrefix());
        assertEquals(300, properties.getTtl().getPaybillWorkflowSeconds());
    }

    @ParameterizedTest
    @ValueSource(strings = { "memory", "MEMORY", "Memory", "MeMoRy" })
    void isMemoryStore_shouldBeTrueForMemoryTypeIgnoringCase(String type) {
        RedisStoreProperties properties = new RedisStoreProperties();
        properties.setType(type);

        assertTrue(properties.isMemoryStore());
    }

    @ParameterizedTest
    @CsvSource({ "redis", "REDIS", "Redis", "in-memory", "mem", "mongo" })
    void isMemoryStore_shouldBeFalseForNonMemoryTypes(String type) {
        RedisStoreProperties properties = new RedisStoreProperties();
        properties.setType(type);

        assertFalse(properties.isMemoryStore());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isMemoryStore_shouldBeFalseForNullOrEmptyType(String type) {
        RedisStoreProperties properties = new RedisStoreProperties();
        properties.setType(type);

        assertEquals(type, properties.getType());
        assertFalse(properties.isMemoryStore());
    }
}
