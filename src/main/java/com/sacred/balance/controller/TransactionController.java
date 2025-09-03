package com.sacred.balance.controller;

import com.sacred.balance.model.Transaction;
import com.sacred.balance.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping
    public String processTransaction(@RequestBody Transaction transaction) {
        transaction.setTimestamp(LocalDateTime.now());
        boolean success = transactionService.processTransaction(transaction);
        return success ? "Success" : "Failed";
    }
}
