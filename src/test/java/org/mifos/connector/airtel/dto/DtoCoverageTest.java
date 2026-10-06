package org.mifos.connector.airtel.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mifos.connector.common.mojaloop.type.TransferState;

class DtoCoverageTest {

    @Test
    @DisplayName("CallbackDto getters, setters and toString")
    void callbackDto_accessors() {
        CallbackDto.Transaction txn = new CallbackDto.Transaction();
        txn.setId("id-1");
        txn.setMessage("ok");
        txn.setStatusCode("TS");
        txn.setAirtelMoneyId("AM-1");
        CallbackDto dto = new CallbackDto();
        dto.setTransaction(txn);
        dto.setHash("hash");

        assertEquals("id-1", dto.getTransaction().getId());
        assertEquals("ok", dto.getTransaction().getMessage());
        assertEquals("TS", dto.getTransaction().getStatusCode());
        assertEquals("AM-1", dto.getTransaction().getAirtelMoneyId());
        assertEquals("hash", dto.getHash());
        assertTrue(dto.toString().contains("id-1"));
        assertTrue(txn.toString().contains("AM-1"));
    }

    @Test
    @DisplayName("CollectionResponseDto nested accessors")
    void collectionResponseDto_accessors() {
        CollectionResponseDto.Data.Transaction txn = new CollectionResponseDto.Data.Transaction();
        txn.setId("t1");
        txn.setMessage("m");
        txn.setStatus("TS");
        txn.setAirtelMoneyId("AM");
        CollectionResponseDto.Data data = new CollectionResponseDto.Data();
        data.setTransaction(txn);
        CollectionResponseDto.Status status = new CollectionResponseDto.Status();
        status.setCode("0");
        status.setSuccess(true);
        status.setResponseCode("200");
        status.setMessage("ok");
        CollectionResponseDto dto = new CollectionResponseDto();
        dto.setData(data);
        dto.setStatus(status);

        assertEquals("t1", dto.getData().getTransaction().getId());
        assertEquals("m", dto.getData().getTransaction().getMessage());
        assertEquals("TS", dto.getData().getTransaction().getStatus());
        assertEquals("AM", dto.getData().getTransaction().getAirtelMoneyId());
        assertEquals("0", dto.getStatus().getCode());
        assertTrue(dto.getStatus().isSuccess());
        assertEquals("200", dto.getStatus().getResponseCode());
        assertEquals("ok", dto.getStatus().getMessage());
        assertTrue(dto.toString().contains("t1"));
        assertTrue(data.toString().contains("transaction"));
        assertTrue(status.toString().contains("success"));
        assertTrue(txn.toString().contains("AM"));
    }

    @Test
    @DisplayName("Record and simple DTO constructors")
    void simpleDtos_constructors() {
        AirtelValidationRequest validationRequest =
                new AirtelValidationRequest("txn", "acc", "123", "ZMW", "msisdn");
        assertEquals("txn", validationRequest.transactionId());

        AirtelValidationResponse validationResponse =
                new AirtelValidationResponse("ok", "txn", "client");
        assertEquals("client", validationResponse.clientName());

        WorkflowResponse workflowResponse = new WorkflowResponse("wf-1");
        assertEquals("wf-1", workflowResponse.transactionId());

        PaybillProps.AmsProps amsProps = new PaybillProps.AmsProps();
        amsProps.setAmsName("roster");
        amsProps.setAmsUrl("http://ams");
        amsProps.setIdentifier("ACCOUNTID");
        ChannelValidationRequest channelValidationRequest =
                ChannelValidationRequest.fromPaybillValidation(validationRequest, amsProps);
        assertNotNull(channelValidationRequest.primaryIdentifier());
        assertNotNull(channelValidationRequest.secondaryIdentifier());
        assertEquals(3, channelValidationRequest.customData().size());

        TransactionStatusResponse statusResponse =
                new TransactionStatusResponse("done", TransferState.COMMITTED, "txn");
        assertEquals(TransferState.COMMITTED, statusResponse.status());
        assertEquals("txn", statusResponse.transactionId());

        AirtelConfirmationResponse confirmationResponse =
                new AirtelConfirmationResponse("accepted");
        assertEquals("accepted", confirmationResponse.message());

        AuthResponseDto auth = new AuthResponseDto("tok", 60, "Bearer");
        assertEquals(60, auth.expiresIn());
        assertEquals("tok", auth.accessToken());
        assertEquals("Bearer", auth.tokenType());

        ErrorResponse errorResponse = new ErrorResponse("bad", Map.of("field", "required"));
        assertEquals("bad", errorResponse.message());
        assertEquals("required", errorResponse.errors().get("field"));
    }

    @Test
    @DisplayName("AirtelConfirmationRequest record accessors")
    void airtelConfirmationRequest_accessors() {
        AirtelConfirmationRequest request = new AirtelConfirmationRequest(
                "txn", BigDecimal.TEN, "ZMW", "2607", "acc", "123");
        assertEquals("txn", request.transactionId());
        assertEquals(BigDecimal.TEN, request.amount());
        assertEquals("ZMW", request.currency());
        assertEquals("2607", request.msisdn());
        assertEquals("acc", request.accountNumber());
        assertEquals("123", request.businessShortCode());
    }
}
