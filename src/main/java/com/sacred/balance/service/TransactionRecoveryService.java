package com.sacred.balance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class TransactionRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionRecoveryService.class);

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    // 交易幂等性键前缀
    private static final String TRANSACTION_IDEMPOTENT_KEY_PREFIX = "transaction:idempotent:";
    // 处理锁超时时间（5分钟）
    private static final int PROCESSING_LOCK_TIMEOUT_MINUTES = 5;


    /**
     * 定时清理僵死的交易处理状态（每2分钟执行一次）
     */
    @Scheduled(fixedRate = 120000) // 每2分钟执行一次
    public void recoverStaleTransactionsPeriodically() {
        logger.debug("Starting periodic transaction recovery process");
        recoverStaleTransactions();
    }

    /**
     * 清理僵死的交易处理状态
     */
    public void recoverStaleTransactions() {
        try {
            // 查找所有处理中的交易
            Set<String> keys = stringRedisTemplate.keys(TRANSACTION_IDEMPOTENT_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                logger.debug("No transaction keys found for recovery");
                return;
            }

            int recoveredCount = 0;
            for (String key : keys) {
                String value = stringRedisTemplate.opsForValue().get(key);
                if ("processing".equals(value)) {
                    // 检查是否超时
                    String startTimeKey = key + ":starttime";
                    String startTimeStr = stringRedisTemplate.opsForValue().get(startTimeKey);

                    if (startTimeStr != null) {
                        try {
                            LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
                            LocalDateTime now = LocalDateTime.now();
                            long minutesElapsed = java.time.Duration.between(startTime, now).toMinutes();

                            if (minutesElapsed > PROCESSING_LOCK_TIMEOUT_MINUTES) {
                                // 超时，清理状态
                                stringRedisTemplate.delete(key);
                                stringRedisTemplate.delete(startTimeKey);
                                logger.info("Recovered stale transaction: {}", key.replace(TRANSACTION_IDEMPOTENT_KEY_PREFIX, ""));
                                recoveredCount++;
                            }
                        } catch (Exception e) {
                            logger.warn("Error parsing start time for key: {}", key, e);
                        }
                    } else {
                        // 没有开始时间，可能是旧数据，直接清理
                        stringRedisTemplate.delete(key);
                        logger.info("Cleaned up transaction without start time: {}", key);
                        recoveredCount++;
                    }
                }
            }

            if (recoveredCount > 0) {
                logger.info("Transaction recovery completed. Recovered {} stale transactions", recoveredCount);
            } else {
                logger.debug("Transaction recovery completed. No stale transactions found");
            }
        } catch (Exception e) {
            logger.error("Error during transaction recovery: {}", e.getMessage(), e);
        }
    }
}
