package com.spin.transactionapi.provider.dto;

/** Response HTTP 4XX/5XX de POST /provider/v1/execute. */
public record ProviderErrorResponse(
        String status,
        String code,
        String message
) {
}
