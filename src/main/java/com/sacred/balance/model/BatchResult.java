package com.sacred.balance.model;

import lombok.Data;

import java.util.List;

@Data
public class BatchResult {
    private String batchId;
    private int totalTransactions;
    private int successfulTransactions;
    private int failedTransactions;
    private List<TransactionResult> results;

    public BatchResult() {
        this.batchId = java.util.UUID.randomUUID().toString();
    }

}
