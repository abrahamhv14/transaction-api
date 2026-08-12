package com.spin.transactionapi.service;

import com.spin.transactionapi.domain.Transaction;
import com.spin.transactionapi.domain.TransactionStatus;
import com.spin.transactionapi.domain.TransactionType;
import com.spin.transactionapi.dto.TransactionRequest;
import com.spin.transactionapi.exception.InvalidTransactionException;
import com.spin.transactionapi.provider.ProviderClient;
import com.spin.transactionapi.provider.ProviderExecutionResult;
import com.spin.transactionapi.provider.exception.ProviderRejectedException;
import com.spin.transactionapi.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del servicio de orquestación, usando un stub del
 * ProviderClient (interfaz) en lugar de HTTP real. Esto es posible gracias
 * al desacoplamiento vía interfaz descrito en ProviderClient.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private ProviderClient providerClient;

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(new TransactionRulesValidator(), providerClient, transactionRepository);
    }

    private void stubRepositorySave() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void invalidRequestNeverReachesTheProvider() {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.CREDIT, new BigDecimal("0.50"), "MXN", null);

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(InvalidTransactionException.class);

        verifyNoInteractions(providerClient);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void approvedProviderResponsePersistsExecutedTransaction() {
        TransactionRequest request = new TransactionRequest(
                "acc-123456", TransactionType.CREDIT, new BigDecimal("1500.00"), "MXN", "Transferencia recibida");

        stubRepositorySave();
        when(providerClient.execute(any())).thenReturn(
                new ProviderExecutionResult("txn-789", new BigDecimal("5500.00"), Instant.now()));

        Transaction result = service.execute(request);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(result.getProviderTransactionId()).isEqualTo("txn-789");
        assertThat(result.getBalanceAfter()).isEqualByComparingTo("5500.00");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.EXECUTED);
    }

    @Test
    void rejectedProviderResponsePersistsRejectedTransactionInsteadOfThrowing() {
        TransactionRequest request = new TransactionRequest(
                "acc-123456", TransactionType.DEBIT, new BigDecimal("500.00"), "MXN", null);

        stubRepositorySave();
        when(providerClient.execute(any()))
                .thenThrow(new ProviderRejectedException("INSUFFICIENT_FUNDS",
                        "The account does not have enough balance to complete the transaction"));

        Transaction result = service.execute(request);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.getBalanceAfter()).isNull();

        verify(transactionRepository).save(any(Transaction.class));
    }
}
