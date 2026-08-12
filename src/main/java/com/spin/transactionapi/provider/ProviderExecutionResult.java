package com.spin.transactionapi.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado de una ejecución APROBADA contra el proveedor externo.
 * Los rechazos y fallos de comunicación se modelan como excepciones
 * (ver ProviderRejectedException / ProviderCommunicationException),
 * de forma que el flujo "feliz" del cliente sea explícito.
 */
public record ProviderExecutionResult(
        String providerTransactionId,
        BigDecimal balanceAfter,
        Instant executedAt
) {
}
