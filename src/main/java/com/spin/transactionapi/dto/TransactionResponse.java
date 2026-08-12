package com.spin.transactionapi.dto;

import com.spin.transactionapi.domain.Transaction;
import com.spin.transactionapi.domain.TransactionStatus;
import com.spin.transactionapi.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        String providerTransactionId,
        BigDecimal balanceAfter,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getAccountId(),
                tx.getType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDescription(),
                tx.getStatus(),
                tx.getProviderTransactionId(),
                tx.getBalanceAfter(),
                tx.getErrorCode(),
                tx.getErrorMessage(),
                tx.getCreatedAt()
        );
    }
}
