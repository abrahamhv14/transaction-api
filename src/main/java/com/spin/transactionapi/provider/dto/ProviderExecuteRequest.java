package com.spin.transactionapi.provider.dto;

import com.spin.transactionapi.domain.TransactionType;

import java.math.BigDecimal;

/** Request hacia POST /provider/v1/execute según el contrato del proveedor. */
public record ProviderExecuteRequest(
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency
) {
}
