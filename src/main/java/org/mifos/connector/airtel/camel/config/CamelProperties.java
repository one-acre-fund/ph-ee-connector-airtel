package org.mifos.connector.airtel.camel.config;

/**
 * Contains properties related to camel.
 */
public class CamelProperties {

    private CamelProperties() {
    }

    public static final String CORRELATION_ID = "correlationId";
    public static final String DEPLOYED_PROCESS = "deployedProcess";
    public static final String COLLECTION_REQUEST_BODY = "collectionRequestBody";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String COLLECTION_RESPONSE_BODY = "mpesaApiResponse";
    public static final String IS_RETRY_EXCEEDED = "isRetryExceeded";
    public static final String IS_TRANSACTION_PENDING = "isTransactionPending";
    public static final String LAST_RESPONSE_BODY = "lastResponseBody";
    public static final String COUNTRY = "country";
    public static final String CURRENCY = "currency";
    public static final String COLLECTION_TRANSACTION_ID = "collectionTransactionId";
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String AMS_URL = "amsUrl";
    public static final String AMS_NAME = "amsName";
    public static final String ACCOUNT_HOLDING_INSTITUTION_ID = "accountHoldingInstitutionId";
    public static final String DEFAULT = "default";
    public static final String SECONDARY_IDENTIFIER_NAME = "MSISDN";
    public static final String BRIDGE_ENDPOINT_QUERY_PARAM =
        "?bridgeEndpoint=true&throwExceptionOnFailure=false";
    public static final String GET_ACCOUNT_DETAILS_FLAG = "getAccountDetails";
    public static final String PRIMARY_IDENTIFIER = "primaryIdentifier";
    public static final String PRIMARY_IDENTIFIER_VALUE = "primaryIdentifierValue";
    public static final String CORRELATION_ID_HEADER = "X-CorrelationID";
    public static final String CHANNEL_VALIDATION_RESPONSE = "channelValidationResponse";

}
