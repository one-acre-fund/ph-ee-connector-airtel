package org.mifos.connector.airtel.zeebe;

/**
 * Contains variables referenced in zeebe.
 */
public class ZeebeVariables {

    private ZeebeVariables() {
    }

    public static final String TRANSACTION_ID = "transactionId";
    public static final String ERROR_INFORMATION = "errorInformation";
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_DESCRIPTION = "errorDescription";
    public static final String AIRTEL_MONEY_ID = "externalId";
    public static final String TRANSACTION_FAILED = "transactionFailed";
    public static final String INIT_TRANSFER_WORKER_NAME = "init-airtel-transfer";
    public static final String CHANNEL_REQUEST = "channelRequest";
    public static final String TRANSFER_CREATE_FAILED = "transferCreateFailed";
    public static final String SERVER_TRANSACTION_STATUS_RETRY_COUNT =
        "airtelTransactionStatusRetryCount";
    public static final String TRANSFER_MESSAGE = "airtel-transaction-request";
    public static final String GET_TRANSACTION_STATUS_WORKER_NAME = "get-airtel-transaction-status";
    public static final String TIMER = "timer";
    public static final String ZEEBE_ELEMENT_INSTANCE_KEY = "elementInstanceKey";
    public static final String GET_TRANSACTION_STATUS_RESPONSE = "getTransactionStatusResponse";
    public static final String GET_TRANSACTION_STATUS_RESPONSE_CODE =
        "getTransactionStatusHttpCode";
    public static final String TRANSFER_RESPONSE_CREATE = "transferResponse-CREATE";
    public static final String CALLBACK_RECEIVED = "isCallbackReceived";
    public static final String CALLBACK = "callback";
    public static final String INITIATOR_FSP_ID = "initiatorFspId";
    public static final String PARTY_LOOKUP_FAILED = "partyLookupFailed";
    public static final String CONFIRMATION_RECEIVED = "confirmationReceived";
    public static final String AMS = "ams";
    public static final String TENANT_ID = "tenantId";
    public static final String CLIENT_CORRELATION_ID = "clientCorrelationId";
    public static final String EXTERNAL_ID = "externalId";
    public static final String CONFIRMATION_TIMER = "airtelConfirmationTimer";
    public static final String PAYMENT_SCHEME = "paymentScheme";
    public static final String AIRTEL_CONSTANT = "airtel";
}
