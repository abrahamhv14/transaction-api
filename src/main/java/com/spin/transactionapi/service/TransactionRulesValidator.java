package com.spin.transactionapi.service;

import com.spin.transactionapi.domain.TransactionType;
import com.spin.transactionapi.dto.TransactionRequest;
import com.spin.transactionapi.exception.InvalidTransactionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Reglas de negocio que deben cumplirse ANTES de llamar al proveedor externo:
 *  1. Monto mínimo: mayor a $1.00.
 *  2. Monto máximo por transacción: DEBIT no puede exceder $10,000.00 (CREDIT sin límite).
 *  3. Moneda soportada: solo MXN.
 *
 * Aislado en su propia clase para que el servicio de orquestación no mezcle
 * validación con la coordinación del flujo, y para poder testear las reglas
 * de forma unitaria sin levantar el resto del contexto.
 */
@Component
public class TransactionRulesValidator {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_DEBIT_AMOUNT = new BigDecimal("10000.00");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("MXN");

    public void validate(TransactionRequest request) {
        validateMinAmount(request.amount());
        validateMaxAmount(request.type(), request.amount());
        validateCurrency(request.currency());
    }

    private void validateMinAmount(BigDecimal amount) {
        if (amount.compareTo(MIN_AMOUNT) <= 0) {
            throw new InvalidTransactionException(
                    "INVALID_AMOUNT",
                    "El monto debe ser mayor a $1.00");
        }
    }

    private void validateMaxAmount(TransactionType type, BigDecimal amount) {
        if (type == TransactionType.DEBIT && amount.compareTo(MAX_DEBIT_AMOUNT) > 0) {
            throw new InvalidTransactionException(
                    "AMOUNT_EXCEEDS_LIMIT",
                    "Las transacciones DEBIT no pueden exceder $10,000.00 por operación");
        }
    }

    private void validateCurrency(String currency) {
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new InvalidTransactionException(
                    "UNSUPPORTED_CURRENCY",
                    "Solo se aceptan transacciones en MXN, se recibió: " + currency);
        }
    }
}
