package com.spin.transactionapi.controller;

import com.spin.transactionapi.domain.Transaction;
import com.spin.transactionapi.domain.TransactionStatus;
import com.spin.transactionapi.domain.TransactionType;
import com.spin.transactionapi.dto.TransactionRequest;
import com.spin.transactionapi.dto.TransactionResponse;
import com.spin.transactionapi.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> execute(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionService.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
        Transaction transaction = transactionService.findById(id);
        return ResponseEntity.ok(TransactionResponse.from(transaction));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> search(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Pageable pageable = PageRequest.of(page, Math.min(limit, 100));
        Page<TransactionResponse> result = transactionService
                .search(accountId, status, type, pageable)
                .map(TransactionResponse::from);

        return ResponseEntity.ok(result);
    }
}
