package com.spin.transactionapi.provider;

import com.spin.transactionapi.provider.dto.ProviderExecuteRequest;

/**
 * Puerto hacia el proveedor externo que gestiona balances y bloqueos.
 * El servicio de dominio depende únicamente de esta interfaz, nunca del
 * cliente HTTP concreto, lo que permite:
 *  - Sustituir la implementación (REST, gRPC, otro proveedor) sin tocar el dominio.
 *  - Testear el servicio con un stub/mock simple, sin levantar HTTP.
 */
public interface ProviderClient {

    /**
     * Ejecuta la transacción contra el proveedor externo.
     *
     * @throws com.spin.transactionapi.provider.exception.ProviderRejectedException
     *         si el proveedor respondió pero rechazó la operación (ej. fondos insuficientes).
     * @throws com.spin.transactionapi.provider.exception.ProviderCommunicationException
     *         si no fue posible comunicarse con el proveedor tras agotar reintentos.
     */
    ProviderExecutionResult execute(ProviderExecuteRequest request);
}
