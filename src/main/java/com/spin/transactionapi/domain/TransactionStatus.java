package com.spin.transactionapi.domain;

public enum TransactionStatus {
    /** El proveedor externo aprobó y ejecutó la transacción. */
    EXECUTED,
    /** El proveedor externo rechazó la transacción (ej. fondos insuficientes). */
    REJECTED,
    /** No se pudo completar la comunicación con el proveedor tras reintentos (timeout, 5xx, circuit open). */
    FAILED
}
