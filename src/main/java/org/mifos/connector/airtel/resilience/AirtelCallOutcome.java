package org.mifos.connector.airtel.resilience;

/**
 * What is known about an Airtel call once retries are over.
 */
public enum AirtelCallOutcome {

    /**
     * Airtel answered; the response code and body are on the message.
     */
    RESPONDED,

    /**
     * The request never reached Airtel: the connection failed, the circuit is open or there was
     * no time left before the job deadline.
     */
    NOT_SENT,

    /**
     * The request was sent but no response came back, so Airtel may or may not have acted on it.
     */
    UNKNOWN
}
