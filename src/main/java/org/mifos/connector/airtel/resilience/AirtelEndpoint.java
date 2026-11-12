package org.mifos.connector.airtel.resilience;

/**
 * Airtel endpoints called through {@link AirtelHttpCaller}; each country and host gets its own
 * circuit breaker per endpoint.
 */
public enum AirtelEndpoint {

    TOKEN("token"), COLLECTION("collection"), TRANSACTION_STATUS("transaction-status");

    private final String id;

    AirtelEndpoint(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
