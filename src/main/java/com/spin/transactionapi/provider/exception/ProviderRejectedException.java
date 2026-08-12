package com.spin.transactionapi.provider.exception;

import lombok.Getter;

/**
 * Representa un rechazo explícito y esperado del proveedor externo
 * (ej. INSUFFICIENT_FUNDS). Distinto de un fallo de comunicación:
 * el proveedor SÍ respondió, pero decidió no aprobar la operación.
 */
@Getter
public class ProviderRejectedException extends RuntimeException {

    private final String providerCode;

    public ProviderRejectedException(String providerCode, String message) {
        super(message);
        this.providerCode = providerCode;
    }
}
