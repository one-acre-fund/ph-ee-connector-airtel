package org.mifos.connector.airtel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "airtel-connector.redis")
public class RedisStoreProperties {

    /**
     * Store backend: {@code redis} (default) or {@code memory}.
     * Applies to paybill workflow store.
     */
    private String type = "redis";
    private String keyPrefix = "airtel-connector";
    private Ttl ttl = new Ttl();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMemoryStore() {
        return "memory".equalsIgnoreCase(type);
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Ttl getTtl() {
        return ttl;
    }

    public void setTtl(Ttl ttl) {
        this.ttl = ttl;
    }

    public static class Ttl {
        private long paybillWorkflowSeconds = 172800;

        public long getPaybillWorkflowSeconds() {
            return paybillWorkflowSeconds;
        }

        public void setPaybillWorkflowSeconds(long paybillWorkflowSeconds) {
            this.paybillWorkflowSeconds = paybillWorkflowSeconds;
        }
    }
}
