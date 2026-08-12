package com.spin.transactionapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spin.transactionapi.provider.ProviderClient;
import com.spin.transactionapi.provider.ProviderExecutionResult;
import com.spin.transactionapi.provider.exception.ProviderRejectedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Se mockea el puerto (interfaz), no el HTTP real: el flujo end-to-end
    // del controlador/servicio/repositorio se prueba con H2 in-memory.
    @MockBean
    private ProviderClient providerClient;

    @Test
    void createsExecutedTransactionWhenProviderApproves() throws Exception {
        when(providerClient.execute(any())).thenReturn(
                new ProviderExecutionResult("txn-789", new BigDecimal("5500.00"), Instant.now()));

        String body = """
                {
                  "accountId": "acc-123456",
                  "type": "CREDIT",
                  "amount": 1500.00,
                  "currency": "MXN",
                  "description": "Transferencia recibida"
                }
                """;

        mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.providerTransactionId").value("txn-789"))
                .andExpect(jsonPath("$.balanceAfter").value(5500.00));
    }

    @Test
    void returnsBadRequestWhenAmountBelowMinimum() throws Exception {
        String body = """
                {
                  "accountId": "acc-123456",
                  "type": "CREDIT",
                  "amount": 0.50,
                  "currency": "MXN"
                }
                """;

        mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void returnsBadRequestWhenCurrencyUnsupported() throws Exception {
        String body = """
                {
                  "accountId": "acc-123456",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD"
                }
                """;

        mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void persistsRejectedTransactionWhenProviderRejects() throws Exception {
        when(providerClient.execute(any())).thenThrow(
                new ProviderRejectedException("INSUFFICIENT_FUNDS", "The account does not have enough balance"));

        String body = """
                {
                  "accountId": "acc-123456",
                  "type": "DEBIT",
                  "amount": 500.00,
                  "currency": "MXN"
                }
                """;

        mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void listsTransactionsFilteredByAccountId() throws Exception {
        when(providerClient.execute(any())).thenReturn(
                new ProviderExecutionResult("txn-1", new BigDecimal("100.00"), Instant.now()));

        String body = """
                {"accountId": "acc-search", "type": "CREDIT", "amount": 50.00, "currency": "MXN"}
                """;
        mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions").param("accountId", "acc-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountId").value("acc-search"));
    }
}
