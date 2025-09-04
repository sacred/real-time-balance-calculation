package com.sacred.balance.service;

import com.sacred.balance.exception.BusinessException;
import com.sacred.balance.model.Account;
import com.sacred.balance.model.Transaction;
import com.sacred.balance.model.TransactionResult;
import com.sacred.balance.model.BatchResult;
import com.sacred.balance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    // 交易幂等性键前缀
    private static final String TRANSACTION_IDEMPOTENT_KEY_PREFIX = "transaction:idempotent:";
    // 交易处理锁键前缀
    private static final String TRANSACTION_LOCK_KEY_PREFIX = "transaction:lock:";
    // 交易结果缓存键前缀
    private static final String TRANSACTION_RESULT_KEY_PREFIX = "transaction:result:";
    // 幂等性键过期时间（48小时），该时间内已完成日间清算
    private static final int IDEMPOTENT_KEY_EXPIRE_HOURS = 48;
    // 处理锁超时时间（5分钟）
    private static final int PROCESSING_LOCK_TIMEOUT_MINUTES = 5;

    /**
     * 处理交易，依赖AccountService的重试机制，支持幂等性
     * @param transaction 交易对象
     * @return 交易处理结果
     */
    @Transactional
    public TransactionResult processTransaction(Transaction transaction) {
        String lockKey = TRANSACTION_LOCK_KEY_PREFIX + transaction.getTransactionId();

        try {
            // 尝试获取分布式锁，防止并发处理同一笔交易
            Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey, "locked", PROCESSING_LOCK_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (lockAcquired == null || !lockAcquired) {
                logger.warn("Failed to acquire lock for transaction: {}", transaction.getTransactionId());
                return new TransactionResult(
                    transaction.getTransactionId(),
                    false,
                    "Transaction is being processed by another instance",
                    "409"
                );
            }

            if (transaction.getSourceAccount() == null || transaction.getDestinationAccount() == null) {
                return new TransactionResult(
                    transaction.getTransactionId(),
                    false,
                    "Source and destination accounts are required",
                    "400"
                );
            }

            if (transaction.getAmount() <= 0) {
                return new TransactionResult(
                    transaction.getTransactionId(),
                    false,
                    "Transaction amount must be positive",
                    "400"
                );
            }

            // 如果没有设置transactionId，则生成一个
            if (transaction.getTransactionId() == null) {
                transaction.setTransactionId(UUID.randomUUID().toString());
            }

            // 检查幂等性
            String idempotentKey = TRANSACTION_IDEMPOTENT_KEY_PREFIX + transaction.getTransactionId();
            String processingFlag = stringRedisTemplate.opsForValue().get(idempotentKey);

            if ("processed".equals(processingFlag)) {
                // 交易已经处理过，直接返回成功
                logger.info("Transaction already processed: {}", transaction.getTransactionId());
                return new TransactionResult(transaction.getTransactionId(), true, "Already processed");
            }

            // 检查是否超时（处理僵死状态）
            if ("processing".equals(processingFlag)) {
                // 检查处理是否超时
                String processingStartTime = stringRedisTemplate.opsForValue().get(idempotentKey + ":starttime");
                if (processingStartTime != null) {
                    try {
                        LocalDateTime startTime = LocalDateTime.parse(processingStartTime);
                        LocalDateTime now = LocalDateTime.now();
                        long minutesElapsed = java.time.Duration.between(startTime, now).toMinutes();

                        if (minutesElapsed > PROCESSING_LOCK_TIMEOUT_MINUTES) {
                            // 超时，清理状态重新处理
                            logger.warn("Transaction processing timeout, resetting state: {}", transaction.getTransactionId());
                            stringRedisTemplate.delete(idempotentKey);
                            stringRedisTemplate.delete(idempotentKey + ":starttime");
                        } else {
                            // 仍在处理时间内
                            logger.warn("Transaction is already processing: {}", transaction.getTransactionId());
                            return new TransactionResult(
                                transaction.getTransactionId(),
                                false,
                                "Transaction is already processing",
                                "409"
                            );
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing processing start time for transaction: {}", transaction.getTransactionId());
                    }
                }
            }

            // 设置处理中标记和开始时间
            stringRedisTemplate.opsForValue().set(idempotentKey, "processing",
                                                IDEMPOTENT_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.opsForValue().set(idempotentKey + ":starttime",
                                                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                                IDEMPOTENT_KEY_EXPIRE_HOURS, TimeUnit.HOURS);

            try {
                Optional<Account> sourceOpt = accountService.findByAccountNumber(transaction.getSourceAccount());
                Optional<Account> destOpt = accountService.findByAccountNumber(transaction.getDestinationAccount());

                if (sourceOpt.isEmpty()) {
                    logger.warn("Source account not found: {}", transaction.getSourceAccount());
                    return new TransactionResult(
                        transaction.getTransactionId(),
                        false,
                        "Source account not found: " + transaction.getSourceAccount(),
                        "404"
                    );
                }

                if (destOpt.isEmpty()) {
                    logger.warn("Destination account not found: {}", transaction.getDestinationAccount());
                    return new TransactionResult(
                        transaction.getTransactionId(),
                        false,
                        "Destination account not found: " + transaction.getDestinationAccount(),
                        "404"
                    );
                }

                Account source = sourceOpt.get();
                Account dest = destOpt.get();

                if (source.getBalance() < transaction.getAmount()) {
                    logger.warn("Insufficient balance for account: {}, balance: {}, amount: {}",
                               source.getAccountNumber(), source.getBalance(), transaction.getAmount());
                    return new TransactionResult(
                        transaction.getTransactionId(),
                        false,
                        "Insufficient balance for account: " + source.getAccountNumber(),
                        "400"
                    );
                }

                // 余额更新（AccountService已有重试机制）
                boolean sourceUpdated = accountService.updateBalance(source.getAccountNumber(), -transaction.getAmount());
                boolean destUpdated = accountService.updateBalance(dest.getAccountNumber(), transaction.getAmount());

                if (sourceUpdated && destUpdated) {
                    transactionRepository.save(transaction);
                    logger.info("Transaction processed successfully. Transaction ID: {}", transaction.getTransactionId());

                    // 设置处理完成标记
                    stringRedisTemplate.opsForValue().set(idempotentKey, "processed",
                                                        IDEMPOTENT_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
                    // 清理开始时间
                    stringRedisTemplate.delete(idempotentKey + ":starttime");

                    return new TransactionResult(transaction.getTransactionId(), true, "Success");
                } else {
                    // 如果更新失败，反向回退
                    accountService.updateBalance(source.getAccountNumber(), transaction.getAmount());
                    accountService.updateBalance(dest.getAccountNumber(), -transaction.getAmount());
                    logger.error("Transaction update failed. Rolling back changes. Transaction ID: {}",
                                transaction.getTransactionId());
                    return new TransactionResult(
                        transaction.getTransactionId(),
                        false,
                        "Transaction update failed. Changes have been rolled back.",
                        "500"
                    );
                }
            } catch (Exception e) {
                // 处理失败，清除处理中标记
                stringRedisTemplate.delete(idempotentKey);
                stringRedisTemplate.delete(idempotentKey + ":starttime");
                logger.error("Unexpected error in transaction processing. Transaction ID: {}, Error: {}",
                            transaction.getTransactionId(), e.getMessage(), e);
                return new TransactionResult(
                    transaction.getTransactionId(),
                    false,
                    "Failed to process transaction: " + e.getMessage(),
                    "500"
                );
            }
        } catch (Exception e) {
            logger.error("Unexpected error in transaction processing. Transaction ID: {}, Error: {}",
                        transaction.getTransactionId(), e.getMessage(), e);
            return new TransactionResult(
                transaction.getTransactionId() != null ? transaction.getTransactionId() : "unknown",
                false,
                "Failed to process transaction: " + e.getMessage(),
                "500"
            );
        } finally {
            // 释放分布式锁
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 检查交易是否已经处理
     */
    public boolean isTransactionProcessed(String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            return false;
        }

        String idempotentKey = TRANSACTION_IDEMPOTENT_KEY_PREFIX + transactionId;
        String result = stringRedisTemplate.opsForValue().get(idempotentKey);
        return "processed".equals(result);
    }

    /**
     * 获取交易处理结果
     * @param transactionId 交易ID
     * @return 交易处理结果
     */
    public TransactionResult getTransactionResult(String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            TransactionResult result = new TransactionResult();
            result.setTransactionId("unknown");
            result.setSuccess(false);
            result.setMessage("Transaction ID is required");
            result.setErrorCode("400");
            return result;
        }

        // 检查幂等性键
        String idempotentKey = TRANSACTION_IDEMPOTENT_KEY_PREFIX + transactionId;
        String processingFlag = stringRedisTemplate.opsForValue().get(idempotentKey);

        if ("processed".equals(processingFlag)) {
            return new TransactionResult(transactionId, true, "Transaction processed successfully");
        }

        if ("processing".equals(processingFlag)) {
            // 检查是否超时
            String processingStartTime = stringRedisTemplate.opsForValue().get(idempotentKey + ":starttime");
            if (processingStartTime != null) {
                try {
                    LocalDateTime startTime = LocalDateTime.parse(processingStartTime);
                    LocalDateTime now = LocalDateTime.now();
                    long minutesElapsed = java.time.Duration.between(startTime, now).toMinutes();

                    if (minutesElapsed > PROCESSING_LOCK_TIMEOUT_MINUTES) {
                        // 超时，返回错误状态
                        TransactionResult result = new TransactionResult();
                        result.setTransactionId(transactionId);
                        result.setSuccess(false);
                        result.setMessage("Transaction processing timeout");
                        result.setErrorCode("408");
                        return result;
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing processing start time for transaction: {}", transactionId);
                }
            }

            TransactionResult result = new TransactionResult();
            result.setTransactionId(transactionId);
            result.setSuccess(false);
            result.setMessage("Transaction is processing");
            result.setErrorCode("409");
            return result;
        }

        // 检查数据库中是否存在该交易
        List<Transaction> transactions = transactionRepository.findAll();
        for (Transaction tx : transactions) {
            if (transactionId.equals(tx.getTransactionId())) {
                // 交易存在于数据库中，说明已处理
                stringRedisTemplate.opsForValue().set(idempotentKey, "processed",
                                                    IDEMPOTENT_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
                return new TransactionResult(transactionId, true, "Transaction processed successfully");
            }
        }

        // 交易不存在
        TransactionResult result = new TransactionResult();
        result.setTransactionId(transactionId);
        result.setSuccess(false);
        result.setMessage("Transaction not found");
        result.setErrorCode("404");
        return result;
    }

    /**
     * 批量处理交易，返回详细的处理结果，支持幂等性
     */
    public BatchResult processBatchTransactions(Transaction... transactions) {
        try {
            if (transactions == null) {
                throw new BusinessException(400, "Transactions are required");
            }

            BatchResult batchResult = new BatchResult();
            List<TransactionResult> results = new ArrayList<>();
            int successfulCount = 0;
            int failedCount = 0;

            for (Transaction transaction : transactions) {
                try {
                    // 确保每个交易都有transactionId
                    if (transaction.getTransactionId() == null) {
                        transaction.setTransactionId(UUID.randomUUID().toString());
                    }

                    // 检查交易是否已经处理
                    if (isTransactionProcessed(transaction.getTransactionId())) {
                        TransactionResult result = new TransactionResult(transaction.getTransactionId(), true, "Already processed");
                        results.add(result);
                        successfulCount++;
                        continue;
                    }

                    TransactionResult result = processTransaction(transaction);
                    results.add(result);

                    if (result.isSuccess()) {
                        successfulCount++;
                    } else {
                        failedCount++;
                    }
                } catch (BusinessException e) {
                    TransactionResult result = new TransactionResult(
                        transaction.getTransactionId() != null ? transaction.getTransactionId() : "unknown",
                        false,
                        e.getMessage(),
                        String.valueOf(e.getCode())
                    );
                    results.add(result);
                    failedCount++;
                    logger.error("Business exception in batch transaction: {}", e.getMessage());
                } catch (Exception e) {
                    TransactionResult result = new TransactionResult(
                        transaction.getTransactionId() != null ? transaction.getTransactionId() : "unknown",
                        false,
                        "Unexpected error: " + e.getMessage(),
                        "500"
                    );
                    results.add(result);
                    failedCount++;
                    logger.error("Unexpected error in batch transaction: {}", e.getMessage(), e);
                }
            }

            batchResult.setTotalTransactions(transactions.length);
            batchResult.setSuccessfulTransactions(successfulCount);
            batchResult.setFailedTransactions(failedCount);
            batchResult.setResults(results);

            logger.info("Batch processing completed. Total: {}, Success: {}, Failed: {}",
                       transactions.length, successfulCount, failedCount);

            return batchResult;
        } catch (BusinessException e) {
            logger.error("Business exception in batch transaction processing: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to process batch transactions: {}", e.getMessage(), e);
            throw new BusinessException(500, "Failed to process batch transactions: " + e.getMessage());
        }
    }
}
