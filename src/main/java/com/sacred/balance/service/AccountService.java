package com.sacred.balance.service;

import com.sacred.balance.model.Account;
import com.sacred.balance.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 并发本地锁控制
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    @Cacheable(value = "accounts", key = "#accountNumber")
    public Optional<Account> findByAccountNumber(String accountNumber) {
        logger.debug("Finding account by account number: {}", accountNumber);
        return accountRepository.findByAccountNumber(accountNumber);
    }

    /**
     * 维护帐户锁，更新余额加锁操作
     */
    @Transactional
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public boolean updateBalance(String accountNumber, double amount) {
        ReentrantLock lock = accountLocks.computeIfAbsent(accountNumber, k -> new ReentrantLock());

        lock.lock();
        try {
            Optional<Account> accountOpt = accountRepository.findByAccountNumber(accountNumber);
            if (accountOpt.isPresent()) {
                Account account = accountOpt.get();
                double oldBalance = account.getBalance();
                account.setBalance(account.getBalance() + amount);
                accountRepository.save(account);

                // 手动将更新后的账户信息放入缓存
                redisTemplate.opsForValue().set("accounts::" + accountNumber, account);

                logger.info("Account balance updated. Account: {}, Old Balance: {}, Amount: {}, New Balance: {}",
                           accountNumber, oldBalance, amount, account.getBalance());
                return true;
            } else {
                logger.warn("Account not found for balance update: {}", accountNumber);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error updating account balance. Account: {}, Amount: {}, Error: {}",
                        accountNumber, amount, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }
}
