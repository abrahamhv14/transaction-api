package com.spin.transactionapi.provider.exception;

/**
 * Representa un fallo de comunicación con el proveedor externo (timeout,
 * 5xx, circuit breaker abierto, error de red) después de agotar reintentos.
 * Distinto de un rechazo de negocio explícito (ver ProviderRejectedException).
 */
public class ProviderCommunicationException extends RuntimeException {

    public ProviderCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
