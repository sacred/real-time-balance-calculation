package com.sacred.balance.model;

import lombok.Data;

@Data
public class TransactionResult {
    private String transactionId;
    private boolean success;
    private String message;
    private String errorCode;

    public TransactionResult() {}

    public TransactionResult(String transactionId, boolean success, String message) {
        this.transactionId = transactionId;
        this.success = success;
        this.message = message;
    }

    public TransactionResult(String transactionId, boolean success, String message, String errorCode) {
        this.transactionId = transactionId;
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
    }
}
