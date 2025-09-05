package com.sacred.balance.controller;

import com.sacred.balance.exception.BusinessException;
import com.sacred.balance.model.ApiResponse;
import com.sacred.balance.model.BatchResult;
import com.sacred.balance.model.Transaction;
import com.sacred.balance.model.TransactionResult;
import com.sacred.balance.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    @Autowired
    private TransactionService transactionService;

    @PostMapping
    public ApiResponse<TransactionResult> processTransaction(@RequestBody Transaction transaction) {
        logger.info("Processing transaction request. Source: {}, Destination: {}, Amount: {}",
                   transaction.getSourceAccount(), transaction.getDestinationAccount(), transaction.getAmount());

        // 确保交易有ID
        if (transaction.getTransactionId() == null) {
            transaction.setTransactionId(UUID.randomUUID().toString());
        }

        TransactionResult result = transactionService.processTransaction(transaction);

        if (result.isSuccess()) {
            logger.info("Transaction processed successfully. Transaction ID: {}", result.getTransactionId());
            return ApiResponse.success("Transaction processed successfully", result);
        } else {
            logger.warn("Transaction processing failed. Transaction ID: {}, Error: {}",
                       result.getTransactionId(), result.getMessage());
            return ApiResponse.error(
                result.getErrorCode() != null ? Integer.parseInt(result.getErrorCode()) : 500,
                "Transaction processing failed: " + result.getMessage()
            );
        }
    }

    /**
     * 获取交易处理结果
     */
    @GetMapping("/result/{transactionId}")
    public ApiResponse<TransactionResult> getTransactionResult(@PathVariable String transactionId) {
        TransactionResult result = transactionService.getTransactionResult(transactionId);
        if (result.isSuccess()) {
            return ApiResponse.success("Transaction result retrieved", result);
        } else {
            return ApiResponse.error(
                result.getErrorCode() != null ? Integer.parseInt(result.getErrorCode()) : 500,
                result.getMessage()
            );
        }
    }

    /**
     * 提交批量处理
     */
    @PostMapping("/batch")
    public ApiResponse<BatchResult> processBatchTransactions(@RequestBody Transaction[] transactions) {
        if (transactions == null || transactions.length == 0) {
            throw new BusinessException(400, "Transactions are required");
        }

        // 为没有ID的交易生成ID
        for (Transaction transaction : transactions) {
            if (transaction.getTransactionId() == null) {
                transaction.setTransactionId(UUID.randomUUID().toString());
            }
        }

        BatchResult result = transactionService.processBatchTransactions(transactions);
        return ApiResponse.success("Batch transactions processed successfully", result);
    }
}
