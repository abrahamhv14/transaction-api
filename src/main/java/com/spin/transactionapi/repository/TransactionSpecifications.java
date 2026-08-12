package com.spin.transactionapi.repository;

import com.spin.transactionapi.domain.Transaction;
import com.spin.transactionapi.domain.TransactionStatus;
import com.spin.transactionapi.domain.TransactionType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Construye filtros dinámicos y opcionales para GET /transactions.
 * Cada filtro solo se aplica si el valor correspondiente no es null,
 * evitando condicionales anidados en el servicio o el repositorio.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> withFilters(
            String accountId, TransactionStatus status, TransactionType type) {

        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (accountId != null && !accountId.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("accountId"), accountId));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates = cb.and(predicates, cb.equal(root.get("type"), type));
            }
            return predicates;
        };
    }
}
