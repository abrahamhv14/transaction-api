package com.spin.transactionapi.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representa una transacción financiera ejecutada (o intentada) contra el
 * proveedor externo. Esta entidad es el registro de auditoría/consulta del
 * sistema; el proveedor externo sigue siendo la fuente de verdad del balance.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_account_id", columnList = "accountId"),
                @Index(name = "idx_transactions_status", columnList = "status"),
                @Index(name = "idx_transactions_type", columnList = "type"),
                @Index(name = "idx_transactions_created_at", columnList = "createdAt")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    /** ID de la transacción devuelto por el proveedor externo (puede ser null si nunca se llegó a ejecutar). */
    private String providerTransactionId;

    /** Balance de la cuenta después de la transacción, según el proveedor externo (puede ser null si fue rechazada/fallida). */
    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** Código de error de negocio, si aplica (ej. INSUFFICIENT_FUNDS, INVALID_AMOUNT, UNSUPPORTED_CURRENCY). */
    private String errorCode;

    /** Mensaje descriptivo del error, si aplica. */
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
