package org.mifos.connector.airtel.dto;

import java.util.Map;

/**
 * Error response DTO.
 *
 * @param message response message
 * @param errors  error details
 */
public record ErrorResponse(String message, Map<String, String> errors) {
}
