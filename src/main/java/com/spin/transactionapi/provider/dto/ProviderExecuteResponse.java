package com.spin.transactionapi.provider.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Response HTTP 200 de POST /provider/v1/execute. */
public record ProviderExecuteResponse(
        String transactionId,
        String status,
        BigDecimal balance,
        Instant executedAt
) {
}
