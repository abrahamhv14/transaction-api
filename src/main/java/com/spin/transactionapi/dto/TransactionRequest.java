package com.spin.transactionapi.dto;

import com.spin.transactionapi.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload de entrada para POST /transactions.
 * La validación estructural (campos requeridos, formato) se hace aquí con
 * Bean Validation. Las reglas de negocio (monto mínimo/máximo, moneda
 * soportada) se aplican explícitamente en el servicio para poder retornar
 * mensajes de error de dominio claros.
 */
public record TransactionRequest(

        @NotBlank(message = "accountId es requerido")
        String accountId,

        @NotNull(message = "type es requerido")
        TransactionType type,

        @NotNull(message = "amount es requerido")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount debe ser mayor a 0")
        BigDecimal amount,

        @NotBlank(message = "currency es requerido")
        String currency,

        String description
) {
}
