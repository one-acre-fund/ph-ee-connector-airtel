package org.mifos.connector.airtel.util;

import static org.mifos.connector.airtel.camel.config.CamelProperties.CURRENCY;
import static org.mifos.connector.airtel.camel.config.CamelProperties.SECONDARY_IDENTIFIER_NAME;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.AMS;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CLIENT_CORRELATION_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CONFIRMATION_RECEIVED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.CONFIRMATION_TIMER;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.INITIATOR_FSP_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.PARTY_LOOKUP_FAILED;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.airtel.zeebe.ZeebeVariables.TRANSACTION_ID;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.mifos.connector.airtel.dto.ChannelValidationResponse;
import org.mifos.connector.common.gsma.dto.CustomData;
import org.mifos.connector.common.gsma.dto.GsmaTransfer;
import org.mifos.connector.common.gsma.dto.Party;

/**
 * Utility class containing methods for Airtel related operations.
 */
public class AirtelUtils {
    private AirtelUtils() {
    }

    /**
     * Create GSMA transfer request payload.
     *
     * @param channelValidationResponse {@link ChannelValidationResponse}
     * @param businessShortCode         business short code
     * @param primaryIdentifier         primary identifier
     * @param primaryIdentifierValue    primary identifier value
     * @param timer                     The duration (in ISO 8601 format) for a confirmation request
     *                                  to be received
     * @return {@link GsmaTransfer}
     */
    public static GsmaTransfer createGsmaTransferRequest(
        ChannelValidationResponse channelValidationResponse,
        String businessShortCode, String primaryIdentifier,
        String primaryIdentifierValue, String timer) {

        Party payer = new Party();
        payer.setPartyIdIdentifier(channelValidationResponse.msisdn());
        payer.setPartyIdType(SECONDARY_IDENTIFIER_NAME);

        Party payee = new Party();
        payee.setPartyIdIdentifier(primaryIdentifierValue);
        payee.setPartyIdType(primaryIdentifier);

        GsmaTransfer gsmaTransfer = new GsmaTransfer();
        List<CustomData> customData = createCustomData(channelValidationResponse,
            businessShortCode, timer);
        gsmaTransfer.setCustomData(customData);
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .format(new Date());
        gsmaTransfer.setRequestDate(currentDateTime);
        gsmaTransfer.setPayee(List.of(payee));
        gsmaTransfer.setPayer(List.of(payer));
        gsmaTransfer.setSubType("inbound");
        gsmaTransfer.setType("airtel");
        gsmaTransfer.setDescriptionText("description");
        gsmaTransfer.setRequestingOrganisationTransactionReference(channelValidationResponse
            .transactionId());
        gsmaTransfer.setAmount(channelValidationResponse.amount());
        gsmaTransfer.setCurrency(channelValidationResponse.currency());
        return gsmaTransfer;
    }

    /**
     * Create custom data for the GSMA transfer request payload.
     *
     * @param validationResponse {@link ChannelValidationResponse}
     * @param businessShortCode  business short code
     * @param timer              The duration (in ISO 8601 format) for a confirmation request
     *                           to be received
     * @return list of custom data
     */
    private static List<CustomData> createCustomData(ChannelValidationResponse validationResponse,
                                                     String businessShortCode, String timer) {
        CustomData reconciled = new CustomData(PARTY_LOOKUP_FAILED,
            !validationResponse.reconciled());
        CustomData confirmationReceived = new CustomData(CONFIRMATION_RECEIVED, false);
        CustomData transactionId = new CustomData(TRANSACTION_ID,
            validationResponse.transactionId());
        CustomData ams = new CustomData(AMS, validationResponse.amsName());
        CustomData tenantId = new CustomData(TENANT_ID, validationResponse
            .accountHoldingInstitutionId());
        CustomData clientCorrelationId = new CustomData(CLIENT_CORRELATION_ID,
            validationResponse.transactionId());
        CustomData currency = new CustomData(CURRENCY, validationResponse.currency());
        CustomData initiatorFspId = new CustomData(INITIATOR_FSP_ID, businessShortCode);
        CustomData confirmationTimer = new CustomData(CONFIRMATION_TIMER, timer);
        return List.of(reconciled, confirmationReceived, transactionId, ams, tenantId,
            clientCorrelationId, currency, initiatorFspId, confirmationTimer);
    }

}
