package com.spin.transactionapi.repository;

import com.spin.transactionapi.domain.Transaction;
import com.spin.transactionapi.domain.TransactionStatus;
import com.spin.transactionapi.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // Se usa JpaSpecificationExecutor para poder combinar filtros opcionales
    // (accountId, status, type) sin explotar en decenas de métodos derivados.
    // Ver TransactionSpecifications.

    Page<Transaction> findByAccountIdAndStatusAndType(
            String accountId, TransactionStatus status, TransactionType type, Pageable pageable);
}
