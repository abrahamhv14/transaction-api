package com.spin.transactionapi.exception;

import lombok.Getter;

/**
 * Se lanza cuando una transacción no cumple las reglas de negocio
 * (monto mínimo, monto máximo por tipo, moneda soportada) ANTES de
 * llamar al proveedor externo.
 */
@Getter
public class InvalidTransactionException extends RuntimeException {

    private final String errorCode;

    public InvalidTransactionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
