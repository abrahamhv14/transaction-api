package com.spin.transactionapi.service;

import com.spin.transactionapi.domain.TransactionType;
import com.spin.transactionapi.dto.TransactionRequest;
import com.spin.transactionapi.exception.InvalidTransactionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRulesValidatorTest {

    private final TransactionRulesValidator validator = new TransactionRulesValidator();

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "1.00", "-5.00"})
    void rejectsAmountsAtOrBelowMinimum(String amount) {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.CREDIT, new BigDecimal(amount), "MXN", null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_AMOUNT");
    }

    @Test
    void rejectsDebitAboveMaxLimit() {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.DEBIT, new BigDecimal("10000.01"), "MXN", null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AMOUNT_EXCEEDS_LIMIT");
    }

    @Test
    void allowsDebitAtExactlyMaxLimit() {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.DEBIT, new BigDecimal("10000.00"), "MXN", null);

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void allowsCreditAboveDebitLimitBecauseCreditHasNoLimit() {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.CREDIT, new BigDecimal("999999.00"), "MXN", null);

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedCurrency() {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.CREDIT, new BigDecimal("100.00"), "USD", null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNSUPPORTED_CURRENCY");
    }

    @Test
    void acceptsValidCreditTransaction() {
        TransactionRequest request = new TransactionRequest(
                "acc-1", TransactionType.CREDIT, new BigDecimal("1500.00"), "MXN", "Transferencia recibida");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }
}
