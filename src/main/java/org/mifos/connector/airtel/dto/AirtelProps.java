package org.mifos.connector.airtel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Holds data required for communicating with Airtel.
 */
@Component
@ConfigurationProperties(prefix = "airtel")
public class AirtelProps {
    private Api api;
    private Map<String, Credentials> credentials;
    private int maxRetryCount;
    private int timeout;

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Credentials getCredentials(String countryFromExchange) {
        return credentials.get(countryFromExchange);
    }

    public void setCredentials(Map<String, Credentials> credentials) {
        this.credentials = credentials;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Holds the relevant Airtel endpoints.
     */
    public static class Api {
        private String baseUrl;
        private String authEndpoint;
        private String collectionEndpoint;
        private String statusEndpoint;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAuthEndpoint() {
            return authEndpoint;
        }

        public void setAuthEndpoint(String authEndpoint) {
            this.authEndpoint = authEndpoint;
        }

        public String getCollectionEndpoint() {
            return collectionEndpoint;
        }

        public void setCollectionEndpoint(String collectionEndpoint) {
            this.collectionEndpoint = collectionEndpoint;
        }

        public String getStatusEndpoint() {
            return statusEndpoint;
        }

        public void setStatusEndpoint(String statusEndpoint) {
            this.statusEndpoint = statusEndpoint;
        }
    }

    /**
     * Holds the credentials required for authentication with Airtel.
     */
    public static class Credentials {
        @JsonProperty("client_id")
        private String clientId;

        @JsonProperty("client_secret")
        private String clientSecret;

        @JsonProperty("grant_type")
        private String grantType;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getGrantType() {
            return grantType;
        }

        public void setGrantType(String grantType) {
            this.grantType = grantType;
        }
    }
}