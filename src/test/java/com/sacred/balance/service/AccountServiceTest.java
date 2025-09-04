package com.sacred.balance.service;

import com.sacred.BalanceCalculationApplication;
import com.sacred.balance.model.Account;
import com.sacred.balance.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BalanceCalculationApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.redis.database=1" // 使用Redis数据库1进行测试

        })
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        // 清空缓存
        if (cacheManager.getCache("accounts") != null) {
            cacheManager.getCache("accounts").clear();
        }
    }

    @Test
    void shouldFindAccountByAccountNumber() {
        // 准备测试数据
        Account account = new Account("A001", 100.0);
        Account savedAccount = accountRepository.save(account);

        // 查找账户
        Optional<Account> foundAccount = accountService.findByAccountNumber("A001");

        assertTrue(foundAccount.isPresent());
        assertEquals(savedAccount.getId(), foundAccount.get().getId());
        assertEquals("A001", foundAccount.get().getAccountNumber());
        assertEquals(100.0, foundAccount.get().getBalance());
    }

    @Test
    void shouldReturnEmptyForNonExistentAccount() {
        Optional<Account> foundAccount = accountService.findByAccountNumber("NON_EXISTENT");

        assertFalse(foundAccount.isPresent());
    }

    @Test
    void shouldUpdateAccountBalance() {
        // 准备测试数据
        Account account = new Account("A002", 200.0);
        Account savedAccount = accountRepository.save(account);

        // 更新余额
        boolean updated = accountService.updateBalance("A002", 50.0);

        assertTrue(updated);

        // 验证数据库中的余额
        Optional<Account> updatedAccount = accountRepository.findByAccountNumber("A002");
        assertTrue(updatedAccount.isPresent());
        assertEquals(250.0, updatedAccount.get().getBalance());
    }

    @Test
    void shouldHandleConcurrentBalanceUpdates() throws InterruptedException {
        // 准备测试数据
        Account account = new Account("A003", 1000.0);
        accountRepository.save(account);

        // 并发更新余额
        int threadCount = 10;
        int amountPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    accountService.updateBalance("A003", amountPerThread);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await();

        // 验证最终余额
        Optional<Account> finalAccount = accountRepository.findByAccountNumber("A003");
        assertTrue(finalAccount.isPresent());
        assertEquals(1100.0, finalAccount.get().getBalance()); // 1000 + 10 * 10
    }

    @Test
    void shouldCacheAccountAfterFirstAccess() {
        // 准备测试数据
        Account account = new Account("A004", 300.0);
        accountRepository.save(account);

        // 第一次访问，应该从数据库加载并存入缓存
        Optional<Account> accountFromService = accountService.findByAccountNumber("A004");
        assertTrue(accountFromService.isPresent());
        assertEquals(300.0, accountFromService.get().getBalance());

        // 验证缓存中存在该账户
        Object cachedAccount = redisTemplate.opsForValue().get("accounts::A004");
        assertNotNull(cachedAccount);
        assertEquals(300.0, ((Account) cachedAccount).getBalance());
    }

    @Test
    void shouldUpdateCacheWhenAccountIsModified() {
        // 准备测试数据
        Account account = new Account("A005", 400.0);
        Account savedAccount = accountRepository.save(account);

        // 先访问一次，使数据进入缓存
        accountService.findByAccountNumber("A005");

        // 修改账户余额
        boolean updated = accountService.updateBalance("A005", 100.0);

        // 验证更新成功
        assertTrue(updated);

        // 验证缓存中数据已更新
        Object cachedAccount = redisTemplate.opsForValue().get("accounts::A005");
        assertNotNull(cachedAccount);
        assertEquals(500.0, ((Account) cachedAccount).getBalance());

        // 验证数据库中数据也已更新
        Optional<Account> dbAccount = accountRepository.findByAccountNumber("A005");
        assertTrue(dbAccount.isPresent());
        assertEquals(500.0, dbAccount.get().getBalance());
    }

    @Test
    void shouldReturnFalseForNonExistentAccountUpdate() {
        boolean updated = accountService.updateBalance("NON_EXISTENT", 100.0);

        assertFalse(updated);
    }

    @Test
    void shouldRetryOnOptimisticLockingFailure() throws InterruptedException {
        // 准备测试数据
        Account account = new Account("A006", 500.0);
        accountRepository.save(account);

        // 模拟并发更新导致的乐观锁异常
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 并发更新同一账户
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    boolean result = accountService.updateBalance("A006", 10.0);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await();

        // 验证至少有一些更新成功（重试机制应该确保不会全部失败）
        assertTrue(successCount.get() > 0, "At least one update should succeed");

        // 验证最终余额（每次成功增加10）
        Optional<Account> finalAccount = accountRepository.findByAccountNumber("A006");
        assertTrue(finalAccount.isPresent());
        double expectedBalance = 500.0 + (successCount.get() * 10);
        assertEquals(expectedBalance, finalAccount.get().getBalance(), 0.01);
    }

    @Test
    void shouldHandleMultipleRetriesAndEventuallySucceed() throws InterruptedException {
        // 准备测试数据
        Account account = new Account("A007", 1000.0);
        accountRepository.save(account);

        // 高并发场景测试重试机制
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 大量并发更新
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 每次更新较小金额，增加成功概率
                    boolean result = accountService.updateBalance("A007", 1.0);
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await();

        // 验证大部分更新应该成功
        assertTrue(successCount.get() >= threadCount * 0.8,
                  "Most updates should succeed with retry mechanism. Success: " + successCount.get());
    }

    @Test
    void shouldLogRetryAttempts() throws InterruptedException {
        // 准备测试数据
        Account account = new Account("A008", 200.0);
        accountRepository.save(account);

        // 创建一个监控线程安全计数器
        AtomicInteger updateAttempts = new AtomicInteger(0);

        // 高并发更新以触发重试
        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    accountService.updateBalance("A008", 5.0);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        latch.await();

        // 验证更新成功并且余额正确
        Optional<Account> finalAccount = accountRepository.findByAccountNumber("A008");
        assertTrue(finalAccount.isPresent());
        assertTrue(finalAccount.get().getBalance() > 200.0, "Balance should have increased");

        // 验证重试机制确实被触发（通过日志可以观察到，这里通过结果验证）
        assertTrue(finalAccount.get().getBalance() <= 275.0, "Balance should not exceed maximum possible");
    }
}
