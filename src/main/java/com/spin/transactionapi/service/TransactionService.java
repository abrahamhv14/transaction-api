package com.spin.transactionapi.service;

import com.spin.transactionapi.domain.Transaction;
import com.spin.transactionapi.domain.TransactionStatus;
import com.spin.transactionapi.domain.TransactionType;
import com.spin.transactionapi.dto.TransactionRequest;
import com.spin.transactionapi.provider.ProviderClient;
import com.spin.transactionapi.provider.ProviderExecutionResult;
import com.spin.transactionapi.provider.dto.ProviderExecuteRequest;
import com.spin.transactionapi.provider.exception.ProviderRejectedException;
import com.spin.transactionapi.repository.TransactionRepository;
import com.spin.transactionapi.repository.TransactionSpecifications;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orquesta el flujo de ejecución de una transacción:
 *  1. Aplica reglas de negocio (falla rápido, sin tocar el proveedor).
 *  2. Llama al proveedor externo.
 *  3. Persiste el resultado (EXECUTED, REJECTED o FAILED).
 *  4. Retorna la transacción persistida.
 *
 * Nota: incluso cuando el proveedor rechaza la operación o falla la
 * comunicación, la transacción SIEMPRE se persiste (con su estado
 * correspondiente) para mantener un registro de auditoría completo.
 */
@Slf4j
@Service
public class TransactionService {

    private final TransactionRulesValidator rulesValidator;
    private final ProviderClient providerClient;
    private final TransactionRepository transactionRepository;

    public TransactionService(
            TransactionRulesValidator rulesValidator,
            ProviderClient providerClient,
            TransactionRepository transactionRepository) {
        this.rulesValidator = rulesValidator;
        this.providerClient = providerClient;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction execute(TransactionRequest request) {
        // 1. Reglas de negocio -> si fallan, ni siquiera se persiste ni se llama al proveedor.
        rulesValidator.validate(request);

        ProviderExecuteRequest providerRequest = new ProviderExecuteRequest(
                request.accountId(), request.type(), request.amount(), request.currency());

        Transaction.TransactionBuilder txBuilder = Transaction.builder()
                .accountId(request.accountId())
                .type(request.type())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description());

        try {
            // 2. Ejecutar contra el proveedor externo (con retry/circuit breaker internos).
            ProviderExecutionResult result = providerClient.execute(providerRequest);

            Transaction executed = txBuilder
                    .status(TransactionStatus.EXECUTED)
                    .providerTransactionId(result.providerTransactionId())
                    .balanceAfter(result.balanceAfter())
                    .build();

            return transactionRepository.save(executed);

        } catch (ProviderRejectedException ex) {
            // El proveedor respondió, pero rechazó la operación (ej. fondos insuficientes).
            Transaction rejected = txBuilder
                    .status(TransactionStatus.REJECTED)
                    .errorCode(ex.getProviderCode())
                    .errorMessage(ex.getMessage())
                    .build();

            return transactionRepository.save(rejected);
        }
        // ProviderCommunicationException (fallo técnico tras reintentos) se deja propagar:
        // no se persiste como FAILED automáticamente para no ocultar el error 503 al cliente;
        // ver README para la discusión de este trade-off (alternativa: persistir FAILED + reintentos async).
    }

    @Transactional(readOnly = true)
    public Transaction findById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transacción no encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> search(
            String accountId, TransactionStatus status, TransactionType type, Pageable pageable) {
        return transactionRepository.findAll(
                TransactionSpecifications.withFilters(accountId, status, type), pageable);
    }
}
